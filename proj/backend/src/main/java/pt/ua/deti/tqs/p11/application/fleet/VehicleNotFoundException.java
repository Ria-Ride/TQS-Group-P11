package pt.ua.deti.tqs.p11.application.fleet;

import java.util.UUID;

public class VehicleNotFoundException extends RuntimeException {

  public VehicleNotFoundException(UUID id) {
    super("No vehicle with id " + id);
  }
}
