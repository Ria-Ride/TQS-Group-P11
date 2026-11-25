package pt.ua.deti.tqs.p11.application.pass;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.billing.ChargeRequest;
import pt.ua.deti.tqs.p11.application.billing.ChargeResult;
import pt.ua.deti.tqs.p11.application.billing.PaymentPort;
import pt.ua.deti.tqs.p11.application.billing.PaymentRepository;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.billing.Payment;
import pt.ua.deti.tqs.p11.domain.pass.Pass;
import pt.ua.deti.tqs.p11.domain.pass.PassType;
import pt.ua.deti.tqs.p11.domain.pass.Stay;

/**
 * What a tourist does to get a pass (US03): see the pass types, buy one, list their passes.
 *
 * <p>Buying happens in three steps, so that no database lock is held while waiting for the gateway:
 *
 * <ol>
 *   <li>In one transaction, the stay is locked, checked for the hotel's date and the account, and
 *       linked to the account (BR9). Two purchases with one code run one after the other, so only
 *       one account can link it.
 *   <li>The price is charged, outside any transaction.
 *   <li>Approved: the pass and the payment are stored (BR8). Not approved: no pass, the payment is
 *       stored as declined, and a link made in step 1 is undone, so the code is free again (BR10).
 * </ol>
 *
 * <p>Time comes from the injected clock; the hotel's calendar day is that instant in {@code
 * hotelZone}.
 */
public class PassPurchaseUseCase {

  private static final String TIMEOUT = "timeout";

  private final PassTypeRepository passTypes;
  private final StayRepository stays;
  private final PassRepository passes;
  private final PaymentRepository payments;
  private final PaymentPort gateway;
  private final TransactionRunner transactions;
  private final Clock clock;
  private final ZoneId hotelZone;
  private final Supplier<UUID> ids;

  public PassPurchaseUseCase(
      PassTypeRepository passTypes,
      StayRepository stays,
      PassRepository passes,
      PaymentRepository payments,
      PaymentPort gateway,
      TransactionRunner transactions,
      Clock clock,
      ZoneId hotelZone,
      Supplier<UUID> ids) {
    this.passTypes = Objects.requireNonNull(passTypes, "passTypes");
    this.stays = Objects.requireNonNull(stays, "stays");
    this.passes = Objects.requireNonNull(passes, "passes");
    this.payments = Objects.requireNonNull(payments, "payments");
    this.gateway = Objects.requireNonNull(gateway, "gateway");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.hotelZone = Objects.requireNonNull(hotelZone, "hotelZone");
    this.ids = Objects.requireNonNull(ids, "ids");
  }

  /** The pass types a tourist can buy, shortest validity first. */
  public List<PassType> passTypes() {
    return passTypes.findAll();
  }

  /**
   * What the pass would cost the account now, with the stay discount when the code is valid (BR9).
   * Reads only: nothing is locked, linked or charged, so the purchase checks again.
   *
   * @param stayCode the code the tourist typed, or null or blank for none
   * @throws UnknownPassTypeException if there is no such pass type
   * @throws UnknownStayCodeException if no stay has the code
   * @throws pt.ua.deti.tqs.p11.domain.pass.StayCodeNotValidTodayException outside the stay
   * @throws pt.ua.deti.tqs.p11.domain.pass.StayCodeLinkedToAnotherAccountException if another
   *     account uses the code
   */
  public PriceQuote quote(UUID accountId, UUID passTypeId, String stayCode) {
    PassType type =
        passTypes.findById(passTypeId).orElseThrow(() -> new UnknownPassTypeException(passTypeId));
    Optional<String> code = StayCodes.normalize(stayCode);
    if (code.isEmpty()) {
      return new PriceQuote(type.id(), type.basePrice(), type.basePrice(), 0);
    }
    Stay stay =
        stays.findByCode(code.get()).orElseThrow(() -> new UnknownStayCodeException(code.get()));
    stay.checkRedeemableBy(accountId, today());
    return new PriceQuote(
        type.id(), type.basePrice(), type.priceWith(stay), stay.discountPercent());
  }

  /** The account's passes, newest first. */
  public List<Pass> passesOf(UUID accountId) {
    return passes.findByAccount(accountId);
  }

  /**
   * Buys a pass for the account.
   *
   * @throws UnknownPassTypeException if there is no such pass type
   * @throws UnknownStayCodeException if a stay code was given and no stay has it
   * @throws pt.ua.deti.tqs.p11.domain.pass.StayCodeNotValidTodayException outside the stay
   * @throws pt.ua.deti.tqs.p11.domain.pass.StayCodeLinkedToAnotherAccountException if another
   *     account uses the code
   * @throws PaymentDeclinedException if the gateway refused the payment
   * @throws PaymentTimedOutException if the gateway gave no answer in time
   */
  public Pass buy(BuyPassCommand command) {
    PassType type =
        passTypes
            .findById(command.passTypeId())
            .orElseThrow(() -> new UnknownPassTypeException(command.passTypeId()));
    Optional<Redemption> redemption =
        command.normalizedStayCode().map(code -> redeem(code, command.accountId()));
    Money price = redemption.map(r -> type.priceWith(r.stay())).orElse(type.basePrice());

    UUID paymentId = ids.get();
    ChargeResult result =
        gateway.charge(new ChargeRequest(paymentId, price, command.paymentToken()));

    UUID account = command.accountId();
    return switch (result) {
      case ChargeResult.Approved approved ->
          issue(account, type, redemption, price, paymentId, approved.reference());
      case ChargeResult.Declined declined -> {
        refuse(account, redemption, price, paymentId, declined.reason());
        throw new PaymentDeclinedException(declined.reason());
      }
      case ChargeResult.TimedOut timedOut -> {
        refuse(account, redemption, price, paymentId, TIMEOUT);
        throw new PaymentTimedOutException();
      }
    };
  }

  /** A stay used for this purchase, and whether this purchase linked it to the account. */
  private record Redemption(Stay stay, boolean newlyLinked) {}

  /** Step 1: lock, check (BR9) and link the stay, in a transaction of its own. */
  private Redemption redeem(String code, UUID accountId) {
    return transactions.inTransaction(() -> {
      Stay stay =
          stays.findByCodeForUpdate(code).orElseThrow(() -> new UnknownStayCodeException(code));
      boolean newlyLinked = stay.redeemBy(accountId, today());
      if (newlyLinked) {
        stays.save(stay);
      }
      return new Redemption(stay, newlyLinked);
    });
  }

  /** Step 3, approved: the pass and the captured payment together (BR8). */
  private Pass issue(
      UUID accountId,
      PassType type,
      Optional<Redemption> redemption,
      Money price,
      UUID paymentId,
      String reference) {
    Instant paidAt = clock.instant();
    UUID stayId = redemption.map(r -> r.stay().id()).orElse(null);
    Pass pass = Pass.issue(ids.get(), accountId, type, stayId, price, paidAt);
    return transactions.inTransaction(() -> {
      passes.save(pass);
      payments.save(Payment.captured(paymentId, accountId, price, reference, pass.id(), paidAt));
      return pass;
    });
  }

  /** Step 3, not approved: no pass; a link made by this purchase is undone (BR10). */
  private void refuse(
      UUID accountId, Optional<Redemption> redemption, Money price, UUID paymentId, String reason) {
    transactions.inTransaction(() -> {
      redemption.filter(Redemption::newlyLinked).ifPresent(r -> unlink(r.stay(), accountId));
      payments.save(Payment.declined(paymentId, accountId, price, reason, clock.instant()));
      return null;
    });
  }

  /** Locks the stay again and frees it, unless the link changed meanwhile. */
  private void unlink(Stay used, UUID accountId) {
    stays
        .findByCodeForUpdate(used.code())
        .filter(stay -> stay.redeemedBy().filter(accountId::equals).isPresent())
        .ifPresent(stay -> {
          stay.unlink();
          stays.save(stay);
        });
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), hotelZone);
  }
}
