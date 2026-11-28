package pt.ua.deti.tqs.p11.adapter.out.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.deti.tqs.p11.application.fleet.LockResult;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCommandPort;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;

/**
 * VehicleCommandPort when telemetry is off (app.telemetry.enabled=false): there is no broker, so a
 * command is only logged. Bookings still work; the vehicles simply receive nothing.
 */
public class LoggingVehicleCommands implements VehicleCommandPort {

  private static final Logger LOG = LoggerFactory.getLogger(LoggingVehicleCommands.class);

  @Override
  public void unlock(FleetCode vehicle) {
    LOG.warn("Telemetry is off: the unlock of {} was not sent", vehicle.value());
  }

  /** With no vehicles to ask, the lock is taken as done, so rides can still be ended. */
  @Override
  public LockResult lock(FleetCode vehicle) {
    LOG.warn("Telemetry is off: the lock of {} was not sent; taken as locked", vehicle.value());
    return new LockResult.Locked();
  }
}
