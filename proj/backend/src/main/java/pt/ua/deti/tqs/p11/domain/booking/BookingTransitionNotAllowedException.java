package pt.ua.deti.tqs.p11.domain.booking;

/** The action is not a transition of the booking lifecycle from the current state. */
public class BookingTransitionNotAllowedException extends RuntimeException {

  private final BookingState from;
  private final BookingAction action;

  public BookingTransitionNotAllowedException(BookingState from, BookingAction action) {
    super("A booking in state %s cannot %s".formatted(from, action));
    this.from = from;
    this.action = action;
  }

  public BookingState from() {
    return from;
  }

  public BookingAction action() {
    return action;
  }
}