package pt.ua.deti.tqs.p11.domain.fleet;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The states of the vehicle lifecycle (Product Specs 3.3). The allowed transitions are a table kept
 * in one place, next(), read per action: "from these states, the action leads to that one".
 */
public enum VehicleState {
  DEACTIVATED,
  AVAILABLE,
  RESERVED,
  IN_USE,
  MAINTENANCE,
  IN_TRANSIT,
  RETIRED;

  /**
   * BR16: the state reached by the action. Activation always leads to Available; retirement is
   * final. A vehicle with an active booking is Reserved or InUse, so it can be neither deactivated
   * nor retired. BR18: a fault sends an Available or Reserved vehicle to Maintenance.
   *
   * @throws ForbiddenTransitionException if the lifecycle has no such transition
   */
  public VehicleState after(VehicleAction action) {
    return next(action).orElseThrow(() -> new ForbiddenTransitionException(this, action));
  }

  /** Whether the lifecycle has a transition for the action from this state. */
  public boolean allows(VehicleAction action) {
    return next(action).isPresent();
  }

  /**
   * BR14: whether a vehicle in this state is physically at a hub and takes one of its slots. A
   * switch with no default, so a new state does not compile until it is classified here.
   */
  public boolean isAtHub() {
    return switch (this) {
      case DEACTIVATED, AVAILABLE, RESERVED, MAINTENANCE -> true;
      case IN_USE, IN_TRANSIT, RETIRED -> false;
    };
  }

  /** The lifecycle table: for each action, the states it starts from and the state it leads to. */
  private Optional<VehicleState> next(VehicleAction action) {
    return switch (action) {
      case ACTIVATE -> to(AVAILABLE, EnumSet.of(DEACTIVATED));
      case DEACTIVATE -> to(DEACTIVATED, EnumSet.of(AVAILABLE));
      case START_MOVE -> to(IN_TRANSIT, EnumSet.of(AVAILABLE));
      case CONFIRM_ARRIVAL -> to(AVAILABLE, EnumSet.of(IN_TRANSIT));
      case RETIRE -> to(RETIRED, EnumSet.of(DEACTIVATED, AVAILABLE, MAINTENANCE));
      case START_USE -> to(IN_USE, EnumSet.of(AVAILABLE));
      case REPORT_FAULT -> to(MAINTENANCE, EnumSet.of(AVAILABLE, RESERVED));
      case RETURN_TO_SERVICE -> throw new UnsupportedOperationException("US11");
    };
  }

  private Optional<VehicleState> to(VehicleState target, Set<VehicleState> from) {
    return from.contains(this) ? Optional.of(target) : Optional.empty();
  }
}
