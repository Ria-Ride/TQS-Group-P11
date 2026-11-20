package pt.ua.deti.tqs.p11.domain.fleet;

/** BR14: a hub's capacity cannot be lowered below the number of vehicles it holds. */
public class CapacityBelowVehiclesHeldException extends RuntimeException {

  public CapacityBelowVehiclesHeldException(FleetCode hub, int capacity, int held) {
    super(
        "Hub %s holds %d vehicles, so its capacity cannot be lowered to %d"
            .formatted(hub.value(), held, capacity));
  }
}
