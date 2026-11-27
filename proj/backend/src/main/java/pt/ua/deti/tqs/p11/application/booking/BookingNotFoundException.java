package pt.ua.deti.tqs.p11.application.booking;

import java.util.UUID;

/** No booking has this id. */
public class BookingNotFoundException extends RuntimeException {

  public BookingNotFoundException(UUID id) {
    super("No booking " + id);
  }
}
