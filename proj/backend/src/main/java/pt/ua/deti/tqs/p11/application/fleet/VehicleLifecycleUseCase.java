package pt.ua.deti.tqs.p11.application.fleet;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
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

  public Vehicle register(RegisterVehicleCommand command) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-82)");
  }

  public Vehicle activate(UUID vehicleId) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-82)");
  }

  public Vehicle deactivate(UUID vehicleId) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-82)");
  }

  public Vehicle startMove(UUID vehicleId) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-83)");
  }

  public Vehicle confirmArrival(UUID vehicleId, UUID destinationHubId) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-83)");
  }

  public Vehicle retire(UUID vehicleId) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-83)");
  }

  public List<VehicleStateChange> history(UUID vehicleId) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-83)");
  }

  public List<Vehicle> list() {
    throw new UnsupportedOperationException("Not implemented yet (KAN-82)");
  }

  public Vehicle get(UUID vehicleId) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-82)");
  }
}
