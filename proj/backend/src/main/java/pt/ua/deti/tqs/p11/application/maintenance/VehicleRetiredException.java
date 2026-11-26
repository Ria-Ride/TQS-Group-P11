package pt.ua.deti.tqs.p11.application.maintenance;

import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;

/** A Retired vehicle is out of the fleet for good (BR16), so it gets no maintenance ticket. */
public class VehicleRetiredException extends RuntimeException {

  public VehicleRetiredException(FleetCode code) {
    super("Vehicle %s is retired".formatted(code.value()));
  }
}
