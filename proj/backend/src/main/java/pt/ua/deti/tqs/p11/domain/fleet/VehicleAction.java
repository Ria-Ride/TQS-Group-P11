package pt.ua.deti.tqs.p11.domain.fleet;

/**
 * What an operator can do to a vehicle (US08). Bookings and faults add their own actions in their
 * stories (US04, US05, US10).
 */
public enum VehicleAction {
  ACTIVATE,
  DEACTIVATE,
  START_MOVE,
  CONFIRM_ARRIVAL,
  RETIRE
}
