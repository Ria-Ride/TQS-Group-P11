package pt.ua.deti.tqs.p11.application.pass;

import java.util.UUID;

/** There is no pass type with this id. */
public class PassTypeNotFoundException extends RuntimeException {

  public PassTypeNotFoundException(UUID id) {
    super("No pass type with id " + id);
  }
}
