package pt.ua.deti.tqs.p11.application.fleet;

import java.util.UUID;

public class HubNotFoundException extends RuntimeException {

  public HubNotFoundException(UUID id) {
    super("No hub with id " + id);
  }
}
