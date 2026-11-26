package pt.ua.deti.tqs.p11.domain.fleet;

/**
 * What can happen to a vehicle in its lifecycle: operator actions (US08) and fault reports (US10).
 * Bookings add their own actions in their stories (US04, US05).
 */
public enum VehicleAction {
  ACTIVATE,
  DEACTIVATE,
  START_MOVE,
  CONFIRM_ARRIVAL,
  RETIRE,
  REPORT_FAULT
}
