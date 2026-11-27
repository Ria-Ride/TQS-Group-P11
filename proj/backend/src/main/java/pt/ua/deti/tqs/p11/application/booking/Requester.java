package pt.ua.deti.tqs.p11.application.booking;

import java.util.Objects;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.booking.Booking;

/**
 * Who asks to act on a booking, from the access token (BR23): the account, and whether it may act
 * on any booking (ADMIN in the access matrix) or only on its own.
 */
public record Requester(UUID accountId, boolean mayActOnAnyBooking) {

  public Requester {
    Objects.requireNonNull(accountId, "accountId");
  }

  /** BR23: a tourist acts only on their own bookings. */
  public boolean mayActOn(Booking booking) {
    return mayActOnAnyBooking || booking.terms().accountId().equals(accountId);
  }
}
