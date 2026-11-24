package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import java.util.UUID;
import pt.ua.deti.tqs.p11.application.fleet.SearchedVehicle;
import pt.ua.deti.tqs.p11.domain.fleet.Bookability;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * A vehicle in the search result of a hub (US02). batteryPercent is null before the first telemetry
 * report. requiredBatteryPercent is what the ride asked for needs (the floor when no duration was
 * given), and may be above 100. unbookableReason is null when bookable is true, and otherwise one
 * of NOT_AVAILABLE (BR2), NO_BATTERY_READING, LOW_BATTERY or INSUFFICIENT_FOR_DURATION (BR1).
 */
public record HubVehicleResponse(
    UUID id,
    String code,
    VehicleType type,
    VehicleState state,
    Integer batteryPercent,
    boolean bookable,
    int requiredBatteryPercent,
    Bookability unbookableReason) {

  static HubVehicleResponse from(SearchedVehicle found) {
    Vehicle vehicle = found.vehicle();
    Bookability bookability = found.bookability();
    return new HubVehicleResponse(
        vehicle.id(),
        vehicle.code().value(),
        vehicle.type(),
        vehicle.state(),
        vehicle.battery().map(level -> level.percent()).orElse(null),
        bookability.isBookable(),
        found.requiredPercent(),
        bookability.isBookable() ? null : bookability);
  }
}
