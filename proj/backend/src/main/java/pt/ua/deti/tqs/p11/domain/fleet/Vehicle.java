package pt.ua.deti.tqs.p11.domain.fleet;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A fleet vehicle and its place in the lifecycle (US08). Immutable: every transition returns a new
 * Vehicle, and the constructor checks the rules, so an invalid Vehicle cannot exist. hubId is the
 * hub the vehicle is at, or null when its state belongs to no hub (InUse, InTransit, Retired).
 */
public record Vehicle(UUID id, FleetCode code, VehicleType type, VehicleState state, UUID hubId) {

  public Vehicle {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(state, "state");
    if (state.isAtHub() && hubId == null) {
      throw new IllegalArgumentException("A vehicle in state %s must be at a hub".formatted(state));
    }
    if (!state.isAtHub() && hubId != null) {
      throw new IllegalArgumentException(
          "A vehicle in state %s cannot be at a hub".formatted(state));
    }
  }

  /** A new vehicle starts Deactivated at the hub where it is registered (BR16). */
  public static Vehicle register(UUID id, FleetCode code, VehicleType type, UUID hubId) {
    return new Vehicle(id, code, type, VehicleState.DEACTIVATED, Objects.requireNonNull(hubId));
  }

  public Optional<UUID> currentHub() {
    return Optional.ofNullable(hubId);
  }

  public Vehicle activate() {
    return transition(VehicleAction.ACTIVATE, hubId);
  }

  public Vehicle deactivate() {
    return transition(VehicleAction.DEACTIVATE, hubId);
  }

  /** The vehicle leaves its hub while the operator moves it. */
  public Vehicle startMove() {
    return transition(VehicleAction.START_MOVE, hubId);
  }

  /** Whether the destination has a free slot is checked by the caller, which knows the hub. */
  public Vehicle confirmArrival(UUID destinationHubId) {
    Objects.requireNonNull(destinationHubId, "destinationHubId");
    return transition(VehicleAction.CONFIRM_ARRIVAL, destinationHubId);
  }

  /** Retirement is final; the vehicle leaves its hub. */
  public Vehicle retire() {
    return transition(VehicleAction.RETIRE, hubId);
  }

  /**
   * Applies the lifecycle table. The vehicle is at hubAfter if the new state is at a hub, and at no
   * hub otherwise.
   */
  private Vehicle transition(VehicleAction action, UUID hubAfter) {
    VehicleState next = state.after(action);
    return new Vehicle(id, code, type, next, next.isAtHub() ? hubAfter : null);
  }
}
