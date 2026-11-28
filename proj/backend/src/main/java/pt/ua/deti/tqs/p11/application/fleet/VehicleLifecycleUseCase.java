package pt.ua.deti.tqs.p11.application.fleet;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleStateChange;

/**
 * What an operator does with vehicles (US08): register, activate, deactivate, move to another hub,
 * retire, and read the state history. Every change is one transaction that locks the vehicle (and
 * then the hub, when a slot is taken), saves it and records the change in the history (BR16, K3).
 */
public class VehicleLifecycleUseCase {

  private final VehicleRepository vehicles;
  private final HubRepository hubs;
  private final VehicleStateChangeRepository history;
  private final OpenTickets openTickets;
  private final TransactionRunner transactions;
  private final Clock clock;
  private final Supplier<UUID> idGenerator;

  public VehicleLifecycleUseCase(
      VehicleRepository vehicles,
      HubRepository hubs,
      VehicleStateChangeRepository history,
      OpenTickets openTickets,
      TransactionRunner transactions,
      Clock clock,
      Supplier<UUID> idGenerator) {
    this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
    this.hubs = Objects.requireNonNull(hubs, "hubs");
    this.history = Objects.requireNonNull(history, "history");
    this.openTickets = Objects.requireNonNull(openTickets, "openTickets");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
  }

  /** BR14, BR16: a new vehicle is Deactivated at a hub with a free slot. */
  public Vehicle register(RegisterVehicleCommand command) {
    FleetCode code = FleetCode.of(command.code());
    return transactions.inTransaction(() -> {
      if (vehicles.existsByCode(code)) {
        throw new VehicleCodeAlreadyInUseException(code);
      }
      Hub hub = lockHubWithFreeSlot(command.hubId());
      Vehicle vehicle =
          vehicles.save(Vehicle.register(idGenerator.get(), code, command.type(), hub.id()));
      history.save(VehicleStateChange.registration(vehicle.id(), clock.instant()));
      return vehicle;
    });
  }

  /** BR16: activation needs no open maintenance ticket. */
  public Vehicle activate(UUID vehicleId) {
    return change(
        vehicleId,
        vehicle -> {
          Vehicle activated = vehicle.activate();
          if (openTickets.existFor(vehicleId)) {
            throw new VehicleHasOpenTicketException(vehicle.code());
          }
          return activated;
        });
  }

  public Vehicle deactivate(UUID vehicleId) {
    return change(vehicleId, Vehicle::deactivate);
  }

  public Vehicle startMove(UUID vehicleId) {
    return change(vehicleId, Vehicle::startMove);
  }

  /** BR14: the destination needs a free slot, counted while it is locked. */
  public Vehicle confirmArrival(UUID vehicleId, UUID destinationHubId) {
    return change(
        vehicleId,
        vehicle -> {
          Vehicle arrived = vehicle.confirmArrival(destinationHubId);
          lockHubWithFreeSlot(destinationHubId);
          return arrived;
        });
  }

  public Vehicle retire(UUID vehicleId) {
    return change(vehicleId, Vehicle::retire);
  }

  /**
   * US04: hands the vehicle to a booking, as one atomic change (K3). The vehicle is locked, then
   * {@code check} runs on it as it is now (the booking rules: state, battery, overlap; or, for a
   * reservation, its start window), then it goes from Available or Reserved (US05) to InUse and the
   * change is recorded. When called inside the booking's own transaction, the booking and the
   * vehicle are committed together.
   *
   * @param check throws to refuse; nothing is saved or recorded then
   */
  public Vehicle startUse(UUID vehicleId, Consumer<Vehicle> check) {
    return checkedChange(vehicleId, check, Vehicle::startUse);
  }

  /** A booking's change: the check runs on the locked vehicle, then the transition. */
  private Vehicle checkedChange(
      UUID vehicleId, Consumer<Vehicle> check, UnaryOperator<Vehicle> transition) {
    Objects.requireNonNull(check, "check");
    return change(
        vehicleId,
        vehicle -> {
          check.accept(vehicle);
          return transition.apply(vehicle);
        });
  }

  /**
   * US05: holds the vehicle at its hub for a booking that starts later, as startUse does for an
   * immediate one: locked, checked, then Available to Reserved, recorded.
   *
   * @param check throws to refuse; nothing is saved or recorded then
   */
  public Vehicle reserve(UUID vehicleId, Consumer<Vehicle> check) {
    return checkedChange(vehicleId, check, Vehicle::reserve);
  }

  /**
   * US05: gives a Reserved vehicle back to its hub when its booking is cancelled or expires.
   *
   * @param check runs on the locked vehicle before it changes (the booking is read again there);
   *     throws to refuse, and nothing is saved or recorded then
   */
  public Vehicle release(UUID vehicleId, Consumer<Vehicle> check) {
    return checkedChange(vehicleId, check, Vehicle::release);
  }

  /**
   * US06: a ride ends at a hub, as one atomic change (K3): the vehicle is locked, {@code check}
   * runs on it (the booking is completed there), it goes from InUse to Available at that hub, or to
   * Maintenance there if a ticket of the vehicle is still open (BR18), and the hub is locked to
   * count its free slot (BR14), vehicle before hub as always.
   *
   * @throws pt.ua.deti.tqs.p11.domain.fleet.HubFullException if the hub has no free slot
   */
  public Vehicle endUse(UUID vehicleId, UUID hubId, Consumer<Vehicle> check) {
    Objects.requireNonNull(hubId, "hubId");
    return checkedChange(
        vehicleId,
        check,
        vehicle -> {
          // BR18: with a ticket still open the vehicle is not offered again.
          Vehicle ended =
              openTickets.existFor(vehicleId)
                  ? vehicle.endUseForRepair(hubId)
                  : vehicle.endUse(hubId);
          lockHubWithFreeSlot(hubId);
          return ended;
        });
  }

  public List<VehicleStateChange> history(UUID vehicleId) {
    get(vehicleId);
    return history.findByVehicleId(vehicleId);
  }

  public List<Vehicle> list() {
    return vehicles.findAll();
  }

  public Vehicle get(UUID vehicleId) {
    return vehicles.findById(vehicleId).orElseThrow(() -> new VehicleNotFoundException(vehicleId));
  }

  /**
   * Locks the vehicle, applies the transition, saves it and records it, all in one transaction. If
   * the transition throws, nothing is saved or recorded (BR16). The vehicle is locked before any
   * hub, always in that order, so two operations never wait for each other.
   */
  private Vehicle change(UUID vehicleId, UnaryOperator<Vehicle> transition) {
    return transactions.inTransaction(() -> {
      Vehicle before =
          vehicles
              .findByIdForUpdate(vehicleId)
              .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
      Vehicle after = vehicles.save(transition.apply(before));
      history.save(VehicleStateChange.between(before, after, clock.instant()));
      return after;
    });
  }

  private Hub lockHubWithFreeSlot(UUID hubId) {
    Hub hub = hubs.findByIdForUpdate(hubId).orElseThrow(() -> new HubNotFoundException(hubId));
    hub.requireFreeSlot(vehicles.countAtHub(hubId));
    return hub;
  }
}
