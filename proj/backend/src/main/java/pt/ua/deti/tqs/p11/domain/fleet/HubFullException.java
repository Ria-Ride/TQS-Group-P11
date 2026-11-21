package pt.ua.deti.tqs.p11.domain.fleet;

/** BR14: the hub already holds as many vehicles as its capacity. */
public class HubFullException extends RuntimeException {

  public HubFullException(FleetCode hub, int capacity) {
    super("Hub %s is full: it holds %d of %d vehicles".formatted(hub.value(), capacity, capacity));
  }
}
