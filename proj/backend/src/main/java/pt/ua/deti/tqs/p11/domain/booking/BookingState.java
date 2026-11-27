package pt.ua.deti.tqs.p11.domain.booking;

import java.util.EnumSet;
import java.util.Set;

/**
 * The states of the booking lifecycle (Product Specs 3.3). The allowed transitions are a table kept
 * in one place, after(), read per action: "from these states, the action leads to that one".
 * Overtime is not a state: a late booking stays Active until it is completed.
 */
public enum BookingState {
  RESERVED,
  ACTIVE,
  COMPLETED,
  CANCELLED,
  EXPIRED;

  /**
   * The state reached by the action. Only a Reserved booking can be started, cancelled or expired;
   * only an Active one can be completed. Completed, Cancelled and Expired are final.
   *
   * @throws BookingTransitionNotAllowedException if the lifecycle has no such transition
   */
  public BookingState after(BookingAction action) {
    return switch (action) {
      case START -> to(ACTIVE, action, EnumSet.of(RESERVED));
      case COMPLETE -> to(COMPLETED, action, EnumSet.of(ACTIVE));
      case CANCEL -> to(CANCELLED, action, EnumSet.of(RESERVED));
      case EXPIRE -> to(EXPIRED, action, EnumSet.of(RESERVED));
    };
  }

  /**
   * BR3, BR6: whether a booking in this state still holds its vehicle and its period. A switch with
   * no default, so a new state does not compile until it is classified here.
   */
  public boolean isOngoing() {
    return switch (this) {
      case RESERVED, ACTIVE -> true;
      case COMPLETED, CANCELLED, EXPIRED -> false;
    };
  }

  private BookingState to(BookingState target, BookingAction action, Set<BookingState> from) {
    if (!from.contains(this)) {
      throw new BookingTransitionNotAllowedException(this, action);
    }
    return target;
  }
}
