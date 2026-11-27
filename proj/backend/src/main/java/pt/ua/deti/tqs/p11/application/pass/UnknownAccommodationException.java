package pt.ua.deti.tqs.p11.application.pass;

import java.util.UUID;

/** A stay code was asked for a hotel that does not exist. */
public class UnknownAccommodationException extends RuntimeException {

  public UnknownAccommodationException(UUID id) {
    super("No partner hotel with id " + id);
  }
}
