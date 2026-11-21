package pt.ua.deti.tqs.p11.domain.fleet;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One entry in a vehicle's state history (BR16): from, to and when. The history is the source of
 * the availability and utilization KPIs (BR22). from is null only for the registration, when the
 * vehicle enters the fleet as Deactivated.
 */
public record VehicleStateChange(UUID vehicleId, VehicleState from, VehicleState to, Instant at) {

  public VehicleStateChange {
    Objects.requireNonNull(vehicleId, "vehicleId");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(at, "at");
    if (from == to) {
      throw new IllegalArgumentException("A state change needs two different states: " + to);
    }
  }

  /** The first entry of every vehicle: it enters the fleet as Deactivated. */
  public static VehicleStateChange registration(UUID vehicleId, Instant at) {
    return new VehicleStateChange(vehicleId, null, VehicleState.DEACTIVATED, at);
  }

  /** The change from one version of a vehicle to the next, after a transition. */
  public static VehicleStateChange between(Vehicle before, Vehicle after, Instant at) {
    if (!before.id().equals(after.id())) {
      throw new IllegalArgumentException("A state change is about one vehicle");
    }
    return new VehicleStateChange(before.id(), before.state(), after.state(), at);
  }
}
