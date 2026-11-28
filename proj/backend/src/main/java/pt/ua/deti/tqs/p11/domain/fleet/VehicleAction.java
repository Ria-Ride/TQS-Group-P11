package pt.ua.deti.tqs.p11.domain.fleet;

/**
 * What can happen to a vehicle in its lifecycle: operator actions (US08), starting a ride (US04),
 * holding it for a later start and giving it back (US05), ending a ride at a hub (US06), fault
 * reports (US10) and the return to service when its last ticket is closed (US11). Which of them an
 * operator may ask for through the API is decided by the web adapter, not here.
 */
public enum VehicleAction {
  ACTIVATE,
  DEACTIVATE,
  START_MOVE,
  CONFIRM_ARRIVAL,
  RETIRE,
  START_USE,
  RESERVE,
  RELEASE,
  END_USE,
  REPORT_FAULT,
  RETURN_TO_SERVICE
}
