package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import java.time.Instant;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleStateChange;

/** One entry of a vehicle's state history (BR16); from is null for the registration. */
public record VehicleStateChangeResponse(VehicleState from, VehicleState to, Instant at) {

  static VehicleStateChangeResponse from(VehicleStateChange change) {
    return new VehicleStateChangeResponse(change.from(), change.to(), change.at());
  }
}
