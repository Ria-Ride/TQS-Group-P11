package pt.ua.deti.tqs.p11.application.booking;

import java.util.List;
import java.util.Objects;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;

/**
 * BR14, BR15: the ride cannot end at this hub because it is full. The ride goes on; the first such
 * refusal is kept on the booking, and the nearest hubs with room are suggested.
 */
public class EndAtFullHubException extends RuntimeException {

  private final transient FleetCode hub;
  private final transient List<NearbyHub> nearby;

  public EndAtFullHubException(FleetCode hub, List<NearbyHub> nearby) {
    super("Hub " + Objects.requireNonNull(hub, "hub").value() + " is full");
    this.hub = hub;
    this.nearby = List.copyOf(nearby);
  }

  public FleetCode hub() {
    return hub;
  }

  /** The nearest hubs with a free slot, nearest first. */
  public List<NearbyHub> nearby() {
    return nearby;
  }
}
