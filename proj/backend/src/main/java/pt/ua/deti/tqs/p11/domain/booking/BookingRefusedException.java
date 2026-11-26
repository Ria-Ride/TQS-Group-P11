package pt.ua.deti.tqs.p11.domain.booking;

import java.util.Objects;

/** A booking was asked for but a business rule refuses it; {@link #reason()} says which. */
public class BookingRefusedException extends RuntimeException {

  private final BookingRefusal reason;

  public BookingRefusedException(BookingRefusal reason) {
    super("The booking is refused: " + Objects.requireNonNull(reason, "reason"));
    this.reason = reason;
  }

  public BookingRefusal reason() {
    return reason;
  }
}