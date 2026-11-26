package pt.ua.deti.tqs.p11.domain.fleet;

/**
 * What can happen to a vehicle in its lifecycle. The operator's actions come from US08; bookings
 * and faults add their own in their stories (US04, US05, US10). Which of them an operator may ask
 * for through the API is decided by the web adapter, not here.
 */
public enum VehicleAction {
  ACTIVATE,
  DEACTIVATE,
  START_MOVE,
  CONFIRM_ARRIVAL,
  RETIRE,
  START_USE
}
