package pt.ua.deti.tqs.p11.domain.fleet;

/**
 * What can happen to a vehicle in its lifecycle: operator actions (US08), starting a ride (US04)
 * and fault reports (US10). Which of them an operator may ask for through the API is decided by the
 * web adapter, not here.
 */
public enum VehicleAction {
  ACTIVATE,
  DEACTIVATE,
  START_MOVE,
  CONFIRM_ARRIVAL,
  RETIRE,
  START_USE,
  REPORT_FAULT
}
