package pt.ua.deti.tqs.p11.application.booking;

import java.util.UUID;

/** BR23: the booking belongs to another account, so the requester may not act on it. */
public class BookingNotOwnedException extends RuntimeException {

  public BookingNotOwnedException(UUID bookingId) {
    super("The booking " + bookingId + " belongs to another account");
  }
}
