package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import java.time.Instant;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.LockState;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * A vehicle; hubId is null while it is InUse, InTransit or Retired. The last four fields are what
 * the last telemetry reading said (US09) and are null until the first one; latitude and longitude
 * are null too while only the hub of the vehicle has been known.
 */
public record VehicleResponse(
    UUID id,
    String code,
    VehicleType type,
    VehicleState state,
    UUID hubId,
    Integer batteryPercent,
    LockState lockState,
    Double latitude,
    Double longitude,
    Instant lastReadingAt) {

  static VehicleResponse from(Vehicle vehicle) {
    return new VehicleResponse(
        vehicle.id(),
        vehicle.code().value(),
        vehicle.type(),
        vehicle.state(),
        vehicle.hubId(),
        vehicle.battery().map(BatteryLevel::percent).orElse(null),
        vehicle.lock().orElse(null),
        vehicle.lastPosition().map(GeoPosition::latitude).orElse(null),
        vehicle.lastPosition().map(GeoPosition::longitude).orElse(null),
        vehicle.lastReading().orElse(null));
  }
}
