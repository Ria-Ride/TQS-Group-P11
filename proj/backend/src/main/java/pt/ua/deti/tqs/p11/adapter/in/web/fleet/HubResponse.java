package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import java.util.UUID;
import pt.ua.deti.tqs.p11.application.fleet.HubOccupancy;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;

/** A hub and how many vehicles it holds now (BR14). */
public record HubResponse(
    UUID id,
    String code,
    String name,
    double latitude,
    double longitude,
    int capacity,
    int vehiclesHeld) {

  static HubResponse from(HubOccupancy occupancy) {
    Hub hub = occupancy.hub();
    return new HubResponse(
        hub.id(),
        hub.code().value(),
        hub.name(),
        hub.position().latitude(),
        hub.position().longitude(),
        hub.capacity(),
        occupancy.vehiclesHeld());
  }
}
