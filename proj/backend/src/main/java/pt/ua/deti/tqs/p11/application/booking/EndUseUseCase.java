package pt.ua.deti.tqs.p11.application.booking;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.fleet.HubNotFoundException;
import pt.ua.deti.tqs.p11.application.fleet.HubRepository;
import pt.ua.deti.tqs.p11.application.fleet.LockResult;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCommandPort;
import pt.ua.deti.tqs.p11.application.fleet.VehicleLifecycleUseCase;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.domain.booking.Booking;
import pt.ua.deti.tqs.p11.domain.booking.BookingAction;
import pt.ua.deti.tqs.p11.domain.booking.BookingState;
import pt.ua.deti.tqs.p11.domain.booking.BookingTransitionNotAllowedException;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;
import pt.ua.deti.tqs.p11.domain.fleet.HubFullException;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;

/**
 * A tourist ends a ride at a hub (US06). Four steps, in an order that never charges for a ride that
 * goes on and never holds a database lock while waiting for the vehicle or the gateway:
 *
 * <ol>
 *   <li>The booking must be the tourist's (BR23) and Active, and the hub must have a free slot
 *       (BR14). A full hub refuses: the first refusal is kept on the booking (BR15) and the nearest
 *       hubs with room are suggested.
 *   <li>The vehicle is asked to lock, and its answer is waited for (simulator contract). A vehicle
 *       that does not lock, or does not answer, leaves the ride going.
 *   <li>In one transaction, the vehicle and then the hub are locked, the free slot is counted again
 *       (another ride may have taken it), the vehicle is Available at that hub and the booking is
 *       Completed.
 *   <li>Once committed, the deposit is settled: the overtime charge is captured, at most the whole
 *       deposit, and the rest released (BR12, BR13).
 * </ol>
 *
 * <p>The hub may be another than the one the ride started from. It is the one the tourist names:
 * the lock answer proves the vehicle is parked at a hub, not which one.
 */
public class EndUseUseCase {

  /** BR15: how many hubs with room are suggested when the chosen one is full. */
  static final int NEARBY_HUBS = 3;

  private final VehicleLifecycleUseCase fleet;
  private final BookingRepository bookings;
  private final HubRepository hubs;
  private final VehicleRepository vehicles;
  private final VehicleCommandPort commands;
  private final DepositSettlement deposits;
  private final TransactionRunner transactions;
  private final Clock clock;

  public EndUseUseCase(
      VehicleLifecycleUseCase fleet,
      BookingRepository bookings,
      HubRepository hubs,
      VehicleRepository vehicles,
      VehicleCommandPort commands,
      DepositSettlement deposits,
      TransactionRunner transactions,
      Clock clock) {
    this.fleet = Objects.requireNonNull(fleet, "fleet");
    this.bookings = Objects.requireNonNull(bookings, "bookings");
    this.hubs = Objects.requireNonNull(hubs, "hubs");
    this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
    this.commands = Objects.requireNonNull(commands, "commands");
    this.deposits = Objects.requireNonNull(deposits, "deposits");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * @return the Completed booking, with its settlement
   * @throws BookingNotFoundException if there is no such booking
   * @throws BookingNotOwnedException if it belongs to another account (BR23)
   * @throws BookingTransitionNotAllowedException if it is not Active
   * @throws HubNotFoundException if there is no such hub
   * @throws EndAtFullHubException if the hub is full (BR14, BR15)
   * @throws VehicleNotLockedException if the vehicle answered but did not lock
   * @throws VehicleLockTimedOutException if the vehicle did not answer in time
   */
  public Booking end(UUID bookingId, UUID hubId, Requester requester) {
    Instant now = clock.instant();
    Booking ride = owned(bookingId, requester);
    if (ride.state() != BookingState.ACTIVE) {
      throw new BookingTransitionNotAllowedException(ride.state(), BookingAction.COMPLETE);
    }
    Hub hub = hubs.findById(hubId).orElseThrow(() -> new HubNotFoundException(hubId));

    // Step 1: a full hub refuses before the vehicle is asked anything.
    if (!hub.hasFreeSlot(vehicles.countAtHub(hubId))) {
      throw refusedAtFullHub(bookingId, hub, now);
    }

    // Step 2: the vehicle must lock.
    Vehicle vehicle = fleet.get(ride.terms().vehicleId());
    switch (commands.lock(vehicle.code())) {
      case LockResult.Locked locked -> {
        // the ride can end
      }
      case LockResult.Refused refused -> throw new VehicleNotLockedException(refused.reason());
      case LockResult.NoAnswer noAnswer -> throw new VehicleLockTimedOutException();
    }

    // Step 3: the locked change. The slot may have been taken since step 1.
    AtomicReference<Booking> completed = new AtomicReference<>();
    try {
      transactions.inTransaction(() -> {
        fleet.endUse(
            vehicle.id(), hubId, locked -> completed.set(current(bookingId).complete(hubId, now)));
        return bookings.save(completed.get());
      });
    } catch (HubFullException taken) {
      throw refusedAtFullHub(bookingId, hub, now);
    }

    // Step 4: the money, once the end is stored.
    deposits.settle(completed.get());
    return completed.get();
  }

  /** BR15: keeps the first refusal on the booking and suggests the nearest hubs with room. */
  private EndAtFullHubException refusedAtFullHub(UUID bookingId, Hub full, Instant now) {
    transactions.inTransaction(() -> bookings.save(current(bookingId).recordFullHubAttempt(now)));
    return new EndAtFullHubException(full.code(), nearbyWithRoom(full));
  }

  /**
   * The nearest other hubs with a free slot, nearest first; one count per hub, as the list does.
   */
  private List<NearbyHub> nearbyWithRoom(Hub full) {
    return hubs.findAll().stream()
        .filter(hub -> !hub.id().equals(full.id()))
        .map(hub ->
            new NearbyHub(
                hub,
                hub.capacity() - vehicles.countAtHub(hub.id()),
                full.position().distanceInMetersTo(hub.position())))
        .filter(nearby -> nearby.freeSlots() > 0)
        .sorted(Comparator.comparingDouble(NearbyHub::distanceMeters))
        .limit(NEARBY_HUBS)
        .toList();
  }

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
