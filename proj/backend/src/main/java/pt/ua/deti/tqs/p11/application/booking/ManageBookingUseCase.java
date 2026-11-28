package pt.ua.deti.tqs.p11.application.booking;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCommandPort;
import pt.ua.deti.tqs.p11.application.fleet.VehicleLifecycleUseCase;
import pt.ua.deti.tqs.p11.domain.booking.Booking;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;

/**
 * What happens to a booking once it exists (US05): a tourist reads it, starts a reservation in its
 * window (BR7) or cancels it (BR13); reservations nobody started expire (BR7). A vehicle fault
 * cancels a reservation too (BR18), in FaultReservationCancellation.
 *
 * <p>Every change locks the vehicle first, through the fleet (K3), and only then reads the booking
 * again, so it acts on the booking as it is now: a reservation started while it was being expired
 * is not expired. This keeps the lock order of the platform (account, vehicle, hub; see the
 * architecture notebook), the one the fault reading already uses.
 *
 * <p>A deposit is given back (DepositReturn) after the change is committed, outside the
 * transaction, as US04 does, so no lock is held while waiting for the gateway.
 */
public class ManageBookingUseCase {

  /** BR13: the reason kept on a booking its tourist cancelled. */
  public static final String CANCELLED_BY_TOURIST = "cancelled by the tourist";

  private static final System.Logger LOG = System.getLogger(ManageBookingUseCase.class.getName());

  private final VehicleLifecycleUseCase fleet;
  private final BookingRepository bookings;
  private final DepositReturn deposits;
  private final VehicleCommandPort commands;
  private final TransactionRunner transactions;
  private final Clock clock;

  public ManageBookingUseCase(
      VehicleLifecycleUseCase fleet,
      BookingRepository bookings,
      DepositReturn deposits,
      VehicleCommandPort commands,
      TransactionRunner transactions,
      Clock clock) {
    this.fleet = Objects.requireNonNull(fleet, "fleet");
    this.bookings = Objects.requireNonNull(bookings, "bookings");
    this.deposits = Objects.requireNonNull(deposits, "deposits");
    this.commands = Objects.requireNonNull(commands, "commands");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  // ------------------------------------------------------------- reading

  /**
   * @throws BookingNotFoundException if there is no such booking
   * @throws BookingNotOwnedException if it belongs to another account (BR23)
   */
  public Booking get(UUID bookingId, Requester requester) {
    return owned(bookingId, requester);
  }

  /** The account's bookings, latest planned start first. */
  public List<Booking> listOf(UUID accountId) {
    return bookings.findByAccount(accountId);
  }

  // ------------------------------------------------------------- start (BR7)

  /**
   * Starts a Reserved booking: Active from now, the vehicle InUse, and, once committed, asked to
   * unlock without waiting for its answer. The deposit stays held for the end of the ride.
   *
   * @throws BookingNotFoundException if there is no such booking
   * @throws BookingNotOwnedException if it belongs to another account (BR23)
   * @throws pt.ua.deti.tqs.p11.domain.booking.BookingTransitionNotAllowedException if it is not
   *     Reserved
   * @throws pt.ua.deti.tqs.p11.domain.booking.BookingRefusedException outside its start window
   */
  public Booking start(UUID bookingId, Requester requester) {
    Booking found = owned(bookingId, requester);
    Instant now = clock.instant();
    AtomicReference<Booking> started = new AtomicReference<>();
    Vehicle vehicle =
        transactions.inTransaction(() -> {
          Vehicle inUse =
              fleet.startUse(
                  found.terms().vehicleId(), locked -> started.set(current(bookingId).start(now)));
          bookings.save(started.get());
          return inUse;
        });
    commands.unlock(vehicle.code());
    return started.get();
  }

  // ------------------------------------------------------------- cancel (BR13)

  /**
   * Cancels a Reserved booking: the vehicle is Available again and the whole deposit is given back.
   *
   * @throws BookingNotFoundException if there is no such booking
   * @throws BookingNotOwnedException if it belongs to another account (BR23)
   * @throws pt.ua.deti.tqs.p11.domain.booking.BookingTransitionNotAllowedException if it is not
   *     Reserved
   */
  public Booking cancel(UUID bookingId, Requester requester) {
    Booking found = owned(bookingId, requester);
    AtomicReference<Booking> cancelled = new AtomicReference<>();
    transactions.inTransaction(() -> {
      fleet.release(
          found.terms().vehicleId(),
          locked -> cancelled.set(current(bookingId).cancel(CANCELLED_BY_TOURIST)));
      return bookings.save(cancelled.get());
    });
    deposits.giveBack(cancelled.get());
    return cancelled.get();
  }

  // ------------------------------------------------------------- expiry (BR7)

  /**
   * Expires every Reserved booking whose start window has passed: Expired, the vehicle Available
   * and the whole deposit given back. Safe to run at any time and more than once: a booking is read
   * again with its vehicle locked and skipped if it is no longer overdue (started, cancelled or
   * already expired). One booking that fails is logged and does not stop the others.
   *
   * @return how many bookings were expired
   */
  public int expireOverdue() {
    Instant now = clock.instant();
    int expired = 0;
    for (Booking candidate : bookings.findReservedStartingBefore(now)) {
      if (!candidate.isOverdue(now)) {
        continue;
      }
      try {
        Optional<Booking> done = expire(candidate, now);
        if (done.isPresent()) {
          deposits.giveBack(done.get());
          expired++;
        }
      } catch (RuntimeException e) {
        LOG.log(Level.ERROR, "Booking {0} could not be expired", candidate.id(), e);
      }
    }
    return expired;
  }

  /** Empty when, with the vehicle locked, the booking turned out not to be overdue any more. */
  private Optional<Booking> expire(Booking candidate, Instant now) {
    AtomicReference<Booking> expired = new AtomicReference<>();
    try {
      transactions.inTransaction(() -> {
        fleet.release(
            candidate.terms().vehicleId(),
            locked -> {
              Booking fresh = current(candidate.id());
              if (!fresh.isOverdue(now)) {
                throw new NoLongerOverdueException();
              }
              expired.set(fresh.expire());
            });
        return bookings.save(expired.get());
      });
    } catch (NoLongerOverdueException e) {
      return Optional.empty();
    }
    return Optional.of(expired.get());
  }

  /** Leaves the transaction without changing anything: the booking is not expired. */
  private static final class NoLongerOverdueException extends RuntimeException {
    NoLongerOverdueException() {
      super(null, null, false, false);
    }
  }

  // ------------------------------------------------------------- helpers

  private Booking current(UUID bookingId) {
    return bookings.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
  }

  private Booking owned(UUID bookingId, Requester requester) {
    Objects.requireNonNull(requester, "requester");
    Booking booking = current(bookingId);
    if (!requester.mayActOn(booking)) {
      throw new BookingNotOwnedException(bookingId);
    }
    return booking;
  }
}
