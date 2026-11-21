package pt.ua.deti.tqs.p11.application.fleet;

import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;

/** Another vehicle already has this code (codes identify vehicles, also for the simulator). */
public class VehicleCodeAlreadyInUseException extends RuntimeException {

  public VehicleCodeAlreadyInUseException(FleetCode code) {
    super("A vehicle with code %s already exists".formatted(code.value()));
  }
}
