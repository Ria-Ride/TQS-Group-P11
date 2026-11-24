package pt.ua.deti.tqs.p11.application.telemetry;

import java.time.Instant;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.LockState;

/**
 * A telemetry reading about one vehicle, as the core sees it: no MQTT, no JSON, no vendor type. The
 * battery is as received, so a value outside 0 to 100 is still a reading, which the core discards
 * (BR17). position is null when only the hub is known.
 */
public record VehicleReading(
    FleetCode vehicleCode,
    double batteryPercent,
    LockState lockState,
    GeoPosition position,
    Instant timestamp) {}
