package pt.ua.deti.tqs.p11.application.fleet;

import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;

/** BR16: a vehicle with an open maintenance ticket cannot be activated. */
public class VehicleHasOpenTicketException extends RuntimeException {

  public VehicleHasOpenTicketException(FleetCode code) {
    super("Vehicle %s has an open maintenance ticket".formatted(code.value()));
  }
}
