package pt.ua.deti.tqs.p11.application.fleet;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryRule;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * What a tourist does to find a vehicle (US02): list the vehicles a hub holds, each judged for BR1
 * (battery, optionally for the duration of the ride) and BR2 (only Available). Vehicles that cannot
 * be booked are listed too, with the reason, so the tourist sees why they cannot be selected.
 * Nothing is stored or locked: the answer is the state at the time of the call, and booking checks
 * the rules again (K3).
 */
public class VehicleSearchUseCase {

  private final HubRepository hubs;
  private final VehicleRepository vehicles;
  private final BatteryRule rule;

  public VehicleSearchUseCase(HubRepository hubs, VehicleRepository vehicles, BatteryRule rule) {
    this.hubs = Objects.requireNonNull(hubs, "hubs");
    this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
    this.rule = Objects.requireNonNull(rule, "rule");
  }

  /** Every vehicle at the hub, ordered by code, judged against the floor only. */
  public List<SearchedVehicle> search(UUID hubId) {
    return search(hubId, null, null);
  }

  /** Only the vehicles of that type. */
  public List<SearchedVehicle> search(UUID hubId, VehicleType type) {
    Objects.requireNonNull(type, "type");
    return search(hubId, type, null);
  }

  /**
   * The vehicles at the hub, ordered by code.
   *
   * @param type only vehicles of this type, or null for every type
   * @param minutes the duration of the ride, from 1 to {@value BatteryRule#MAX_MINUTES}, or null
   *     when the tourist has not said
   * @throws HubNotFoundException if there is no such hub
   * @throws IllegalArgumentException if the duration is outside its limits
   */
  public List<SearchedVehicle> search(UUID hubId, VehicleType type, Integer minutes) {
    OptionalInt ride = minutes == null ? OptionalInt.empty() : OptionalInt.of(minutes);
    ride.ifPresent(BatteryRule::checkMinutes);
    hubs.findById(hubId).orElseThrow(() -> new HubNotFoundException(hubId));
    return vehicles.findAtHub(hubId).stream()
        .filter(vehicle -> type == null || vehicle.type() == type)
        .map(vehicle -> judged(vehicle, ride))
        .toList();
  }

  private SearchedVehicle judged(Vehicle vehicle, OptionalInt ride) {
    int required =
        ride.isPresent()
            ? rule.requiredPercent(vehicle.type(), ride.getAsInt())
            : rule.floorPercent();
    return new SearchedVehicle(vehicle, vehicle.bookability(rule, ride), required);
  }
}
