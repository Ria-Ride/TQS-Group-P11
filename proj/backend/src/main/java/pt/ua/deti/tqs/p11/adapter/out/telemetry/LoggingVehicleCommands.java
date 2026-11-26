package pt.ua.deti.tqs.p11.adapter.out.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
}
