package pt.ua.deti.tqs.p11.domain.maintenance;

/** The states of a maintenance ticket (Product Specs 3.3, maintenance ticket lifecycle). */
public enum TicketState {
  OPEN,
  ASSIGNED,
  IN_PROGRESS,
  CLOSED,
  CANCELLED;

  /** BR20: whether the ticket still needs work (Open, Assigned or InProgress). */
  public boolean isOpen() {
    return switch (this) {
      case OPEN, ASSIGNED, IN_PROGRESS -> true;
      case CLOSED, CANCELLED -> false;
    };
  }
}
