package pt.ua.deti.tqs.p11.domain.fleet;

import java.util.UUID;

/**
 * A place where vehicles are picked up and returned (US08). Immutable; the constructor checks the
 * rules. The hub does not keep its vehicles: how many it holds is counted from the vehicles at it
 * (BR14) and passed in as "held" by the caller.
 */
public record Hub(UUID id, FleetCode code, String name, GeoPosition position, int capacity) {

  public Hub {
    // Validation not implemented yet (KAN-81).
  }

  /** BR14: whether one more vehicle fits, given how many the hub holds now. */
  public boolean hasFreeSlot(int held) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-81)");
  }

  /**
   * BR14: for registering a vehicle here or a vehicle arriving here.
   *
   * @throws HubFullException if the hub holds as many vehicles as its capacity
   */
  public void requireFreeSlot(int held) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-81)");
  }

  /**
   * BR14: the same hub with a new capacity.
   *
   * @throws CapacityBelowVehiclesHeldException if the new capacity is below the vehicles held
   */
  public Hub withCapacity(int newCapacity, int held) {
    throw new UnsupportedOperationException("Not implemented yet (KAN-81)");
  }
}
