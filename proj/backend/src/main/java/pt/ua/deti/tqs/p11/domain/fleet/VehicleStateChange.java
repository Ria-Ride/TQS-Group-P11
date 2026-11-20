package pt.ua.deti.tqs.p11.domain.fleet;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in a vehicle's state history (BR16): from, to and when. The history is the source of
 * the availability and utilization KPIs (BR22). from is null only for the registration, when the
 * vehicle enters the fleet as Deactivated.
 */
public record VehicleStateChange(UUID vehicleId, VehicleState from, VehicleState to, Instant at) {

  public VehicleStateChange {
    // Validation not implemented yet (KAN-79).
  }

  /** The first entry of every vehicle: it enters the fleet as Deactivated. */
  public static VehicleStateChange registration(UUID vehicleId, Instant at) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-79)");
  }

  /** The change from one version of a vehicle to the next, after a transition. */
  public static VehicleStateChange between(Vehicle before, Vehicle after, Instant at) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-79)");
  }
}
