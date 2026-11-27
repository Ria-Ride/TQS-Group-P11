package pt.ua.deti.tqs.p11.domain.maintenance;

/** The action is not part of the maintenance ticket lifecycle from the current state. */
public class ForbiddenTicketTransitionException extends RuntimeException {

  private final TicketState from;

  public ForbiddenTicketTransitionException(TicketState from, String action) {
    super("A ticket in state %s cannot be %s".formatted(from, action));
    this.from = from;
  }

  public TicketState from() {
    return from;
  }
}
