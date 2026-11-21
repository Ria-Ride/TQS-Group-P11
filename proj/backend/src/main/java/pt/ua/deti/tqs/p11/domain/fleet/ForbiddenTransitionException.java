package pt.ua.deti.tqs.p11.domain.fleet;

/** BR16: the action is not a transition of the vehicle lifecycle from the current state. */
public class ForbiddenTransitionException extends RuntimeException {

  private final VehicleState from;
  private final VehicleAction action;

  public ForbiddenTransitionException(VehicleState from, VehicleAction action) {
    super("A vehicle in state %s cannot %s".formatted(from, action));
    this.from = from;
    this.action = action;
  }

  public VehicleState from() {
    return from;
  }

  public VehicleAction action() {
    return action;
  }
}
