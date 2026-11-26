package pt.ua.deti.tqs.p11.application.booking;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.billing.DepositRequest;
import pt.ua.deti.tqs.p11.application.billing.DepositResult;
import pt.ua.deti.tqs.p11.application.billing.PaymentPort;
import pt.ua.deti.tqs.p11.application.billing.PaymentRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCommandPort;
import pt.ua.deti.tqs.p11.application.fleet.VehicleLifecycleUseCase;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.billing.Payment;
import pt.ua.deti.tqs.p11.domain.booking.Booking;
import pt.ua.deti.tqs.p11.domain.booking.BookingPolicy;
import pt.ua.deti.tqs.p11.domain.booking.BookingRules;
import pt.ua.deti.tqs.p11.domain.booking.BookingTerms;
import pt.ua.deti.tqs.p11.domain.booking.PassCoverage;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;

/**
 * A tourist starts using an available vehicle right away (US04). Four steps, so that no database
 * lock is held while waiting for the gateway:
 *
 * <ol>
 *   <li>The booking rules are checked without locks (BookingPolicy). A refused request holds no
 *       money.
 *   <li>The deposit of the vehicle's type is held, outside any transaction (BR11). Declined: kept
 *       as declined, nothing created. No answer in time: released with the same payment id (it may
 *       be held), kept as declined, nothing created.
 *   <li>In one transaction, the account and then the vehicle are locked, the rules are checked
 *       again on the locked rows (another request may have won meanwhile: BR3, BR6), the vehicle
 *       goes InUse through the fleet (K3), and the Active booking and the held deposit are stored.
 *       If this fails, the deposit is given back.
 *   <li>Once committed, the vehicle is asked to unlock, without waiting for its answer.
 * </ol>
 *
 * <p>Time comes from the injected clock; the rules come from the policy supplier on every request,
 * so a change by the client applies to the next booking (BR21).
 */
public class StartUseUseCase {

  private static final System.Logger LOG = System.getLogger(StartUseUseCase.class.getName());
  private static final String TIMEOUT = "timeout";

  private final VehicleLifecycleUseCase fleet;
  private final UserRepository users;
  private final PassCoverages passes;
  private final BookingRepository bookings;
  private final PaymentRepository payments;
  private final PaymentPort gateway;
  private final VehicleCommandPort commands;
  private final Supplier<BookingPolicy> policies;
  private final TransactionRunner transactions;
  private final Clock clock;
  private final Supplier<UUID> ids;

  public StartUseUseCase(
      VehicleLifecycleUseCase fleet,
      UserRepository users,
      PassCoverages passes,
      BookingRepository bookings,
      PaymentRepository payments,
      PaymentPort gateway,
      VehicleCommandPort commands,
      Supplier<BookingPolicy> policies,
      TransactionRunner transactions,
      Clock clock,
      Supplier<UUID> ids) {
    this.fleet = Objects.requireNonNull(fleet, "fleet");
    this.users = Objects.requireNonNull(users, "users");
    this.passes = Objects.requireNonNull(passes, "passes");
    this.bookings = Objects.requireNonNull(bookings, "bookings");
    this.payments = Objects.requireNonNull(payments, "payments");
    this.gateway = Objects.requireNonNull(gateway, "gateway");
    this.commands = Objects.requireNonNull(commands, "commands");
    this.policies = Objects.requireNonNull(policies, "policies");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.ids = Objects.requireNonNull(ids, "ids");
  }

  /**
   * @return the Active booking
   * @throws pt.ua.deti.tqs.p11.application.fleet.VehicleNotFoundException if there is no such
   *     vehicle
   * @throws pt.ua.deti.tqs.p11.domain.booking.BookingRefusedException with the rule that refuses,
   *     before or after the deposit was held
   * @throws DepositDeclinedException if the gateway refused the deposit
   * @throws DepositTimedOutException if the gateway gave no answer in time
   */
  public Booking start(StartUseCommand command) {
    Instant now = clock.instant();
    UUID account = command.accountId();
    BookingPolicy policy = policies.get();

    // Step 1: the rules, without locks.
    Vehicle vehicle = fleet.get(command.vehicleId());
    policy.check(
        vehicle,
        now,
        command.plannedEnd(),
        passes.ofAccount(account),
        bookings.findOngoingByAccount(account));
    BookingRules rules = policy.rules().forType(vehicle.type());
    Money deposit = rules.deposit();

    // Step 2: the deposit, outside any transaction.
    UUID paymentId = ids.get();
    DepositResult held =
        gateway.authorize(new DepositRequest(paymentId, deposit, command.paymentToken()));
    return switch (held) {
      case DepositResult.Approved approved ->
          create(command, now, policy, rules, vehicle.code(), paymentId, approved.reference());
      case DepositResult.Declined declined -> {
        payments.save(Payment.depositDeclined(paymentId, account, deposit, declined.reason(), now));
        throw new DepositDeclinedException(declined.reason());
      }
      case DepositResult.TimedOut timedOut -> {
        gateway.release(paymentId);
        payments.save(Payment.depositDeclined(paymentId, account, deposit, TIMEOUT, now));
        throw new DepositTimedOutException();
      }
    };
  }

  /** Steps 3 and 4: the locked change, then the unlock; the deposit is given back on failure. */
  private Booking create(
      StartUseCommand command,
      Instant now,
      BookingPolicy policy,
      BookingRules rules,
      FleetCode vehicleCode,
      UUID paymentId,
      String reference) {
    Booking booking;
    try {
      booking =
          transactions.inTransaction(
              () -> createOnLockedRows(command, now, policy, rules, paymentId, reference));
    } catch (RuntimeException lost) {
      giveBack(command.accountId(), rules.deposit(), paymentId, reference);
      throw lost;
    }
    commands.unlock(vehicleCode);
    return booking;
  }

  /** Step 3: account then vehicle locked, rules checked again, everything stored together. */
  private Booking createOnLockedRows(
      StartUseCommand command,
      Instant now,
      BookingPolicy policy,
      BookingRules rules,
      UUID paymentId,
      String reference) {
    UUID account = command.accountId();
    users
        .findByIdForUpdate(account)
        .orElseThrow(() -> new IllegalStateException("No account " + account));
    List<PassCoverage> coverages = passes.ofAccount(account);
    List<Booking> ongoing = bookings.findOngoingByAccount(account);
    AtomicReference<PassCoverage> pass = new AtomicReference<>();
    AtomicReference<java.util.UUID> startHub = new AtomicReference<>();
    fleet.startUse(
        command.vehicleId(),
        locked -> {
          pass.set(policy.check(locked, now, command.plannedEnd(), coverages, ongoing));
          startHub.set(locked.currentHub().orElseThrow());
        });
    BookingTerms terms =
        new BookingTerms(
            account,
            command.vehicleId(),
            pass.get().passId(),
            startHub.get(),
            now,
            command.plannedEnd());
    Booking booking = Booking.startNow(ids.get(), terms, rules, reference);
    bookings.save(booking);
    payments.save(
        Payment.depositAuthorized(
            paymentId, account, rules.deposit(), reference, booking.id(), now));
    return booking;
  }

  /**
   * The booking was not created after its deposit was held: give the deposit back (BR13). Only a
   * release that went through is recorded; otherwise the money may still be held, which is logged
   * so it can be released by hand.
   */
  private void giveBack(UUID account, Money deposit, UUID paymentId, String reference) {
    DepositResult released = gateway.release(paymentId);
    if (released instanceof DepositResult.Approved) {
      payments.save(
          Payment.depositReleased(paymentId, account, deposit, reference, null, clock.instant()));
    } else {
      LOG.log(
          Level.ERROR,
          "Deposit {0} of account {1} may still be held: the release answered {2}",
          paymentId,
          account,
          released);
    }
  }
}
