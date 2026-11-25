package pt.ua.deti.tqs.p11.application.pass;

import java.util.UUID;

/** No pass type has this id. */
public class UnknownPassTypeException extends RuntimeException {

  public UnknownPassTypeException(UUID id) {
    super("No pass type with id " + id);
  }
}
