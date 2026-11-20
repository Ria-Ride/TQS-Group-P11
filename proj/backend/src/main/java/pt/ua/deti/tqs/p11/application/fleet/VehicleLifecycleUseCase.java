package pt.ua.deti.tqs.p11.application.fleet;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
