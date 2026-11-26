package pt.ua.deti.tqs.p11.application.telemetry;

import java.time.Instant;
import java.util.Optional;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.LockState;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;

/**
 * A telemetry reading about one vehicle, as the core sees it: no MQTT, no JSON, no vendor type. The
 * battery is as received, so a value outside 0 to 100 is still a reading, which the core discards
 * (BR17). position is null when only the hub is known. faultCode is the fault the vehicle reports
 * now, null when it has none; the simulator repeats it in every reading while the fault lasts.
 */
public record VehicleReading(
    FleetCode vehicleCode,
    double batteryPercent,
    LockState lockState,
    GeoPosition position,
    Instant timestamp,
    FaultCode faultCode) {

  /** A reading without a fault. */
  public VehicleReading(
      FleetCode vehicleCode,
      double batteryPercent,
      LockState lockState,
      GeoPosition position,
      Instant timestamp) {
    this(vehicleCode, batteryPercent, lockState, position, timestamp, null);
  }

  /** The fault the vehicle reports now; empty when it has none. */
  public Optional<FaultCode> fault() {
    return Optional.ofNullable(faultCode);
  }
}
