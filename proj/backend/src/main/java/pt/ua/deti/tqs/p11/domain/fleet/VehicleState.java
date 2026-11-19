package pt.ua.deti.tqs.p11.domain.fleet;

/** The states of the vehicle lifecycle (Product Specs 3.3). */
public enum VehicleState {
  DEACTIVATED,
  AVAILABLE,
  RESERVED,
  IN_USE,
  MAINTENANCE,
  IN_TRANSIT,
  RETIRED;

  /** BR16: the state reached by the action, or ForbiddenTransitionException. */
  public VehicleState after(VehicleAction action) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-80)");
  }

  /** BR14: whether a vehicle in this state is physically at a hub and takes one of its slots. */
  public boolean isAtHub() {
    throw new UnsupportedOperationException("Not implemented yet (KAN-80)");
  }
}
