package pt.ua.deti.tqs.p11.application.telemetry;

import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;

/**
 * Port: told when an applied reading reports a fault (BR18). Owned by telemetry, so telemetry does
 * not depend on whoever handles faults (maintenance). Called inside the transaction that applied
 * the reading, with the vehicle still locked, so two readings of the same vehicle are handled one
 * after the other.
 */
public interface FaultReportListener {

  /**
   * @param vehicle the vehicle with the reading already applied
   * @param fault the fault the reading reports
   */
  void faultReported(Vehicle vehicle, FaultCode fault);
}
