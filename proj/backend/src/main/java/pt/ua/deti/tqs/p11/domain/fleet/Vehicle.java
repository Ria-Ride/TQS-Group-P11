package pt.ua.deti.tqs.p11.domain.fleet;

import java.util.Optional;
import java.util.UUID;

/**
 * A fleet vehicle and its place in the lifecycle (US08). Immutable: every transition returns a new
 * Vehicle. hubId is the hub the vehicle is at, or null when its state belongs to no hub (InUse,
 * InTransit, Retired).
 */
public record Vehicle(UUID id, FleetCode code, VehicleType type, VehicleState state, UUID hubId) {

  public Vehicle {
    // Invariants not implemented yet (KAN-80).
  }

  /** A new vehicle starts Deactivated at the hub where it is registered. */
  public static Vehicle register(UUID id, FleetCode code, VehicleType type, UUID hubId) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-80)");
  }

  public Optional<UUID> currentHub() {
    throw new UnsupportedOperationException("Not implemented yet (KAN-80)");
  }

  public Vehicle activate() {
    throw new UnsupportedOperationException("Not implemented yet (KAN-80)");
  }

  public Vehicle deactivate() {
    throw new UnsupportedOperationException("Not implemented yet (KAN-80)");
  }

  public Vehicle startMove() {
    throw new UnsupportedOperationException("Not implemented yet (KAN-80)");
  }

  public Vehicle confirmArrival(UUID destinationHubId) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-80)");
  }

  public Vehicle retire() {
    throw new UnsupportedOperationException("Not implemented yet (KAN-80)");
  }
}
