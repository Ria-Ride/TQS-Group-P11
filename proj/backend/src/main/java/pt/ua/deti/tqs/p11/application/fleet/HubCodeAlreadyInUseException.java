package pt.ua.deti.tqs.p11.application.fleet;

import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;

/** Another hub already has this code (codes identify hubs, also for the simulator). */
public class HubCodeAlreadyInUseException extends RuntimeException {

  public HubCodeAlreadyInUseException(FleetCode code) {
    super("A hub with code %s already exists".formatted(code.value()));
  }
}
