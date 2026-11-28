package pt.ua.deti.tqs.p11.application.booking;

import java.util.Objects;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;

/**
 * BR15: a hub with room, suggested when the one the tourist chose is full.
 *
 * @param freeSlots how many more vehicles it holds now (BR14)
 * @param distanceMeters from the full hub, along the Earth's surface
 */
public record NearbyHub(Hub hub, int freeSlots, double distanceMeters) {

  public NearbyHub {
    Objects.requireNonNull(hub, "hub");
  }
}
