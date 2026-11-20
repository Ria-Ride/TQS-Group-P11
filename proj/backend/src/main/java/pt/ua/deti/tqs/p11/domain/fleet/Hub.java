package pt.ua.deti.tqs.p11.domain.fleet;

import java.util.Objects;
import java.util.UUID;

/**
 * A place where vehicles are picked up and returned (US08). Immutable; the constructor checks the
 * rules. The hub does not keep its vehicles: how many it holds is counted from the vehicles at it
 * (BR14) and passed in as "held" by the caller.
 */
public record Hub(UUID id, FleetCode code, String name, GeoPosition position, int capacity) {

  private static final int MIN_CAPACITY = 1;

  public Hub {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(code, "code");
    name = Objects.requireNonNull(name, "name").strip();
    Objects.requireNonNull(position, "position");
    if (name.isEmpty()) {
      throw new IllegalArgumentException("A hub needs a name");
    }
    requireValidCapacity(capacity);
  }

  /** BR14: whether one more vehicle fits, given how many the hub holds now. */
  public boolean hasFreeSlot(int held) {
    requireValidHeld(held);
    return held < capacity;
  }

  /**
   * BR14: for registering a vehicle here or a vehicle arriving here.
   *
   * @throws HubFullException if the hub holds as many vehicles as its capacity
   */
  public void requireFreeSlot(int held) {
    if (!hasFreeSlot(held)) {
      throw new HubFullException(code, capacity);
    }
  }

  /**
   * BR14: the same hub with a new capacity.
   *
   * @throws CapacityBelowVehiclesHeldException if the new capacity is below the vehicles held
   */
  public Hub withCapacity(int newCapacity, int held) {
    requireValidCapacity(newCapacity);
    requireValidHeld(held);
    if (newCapacity < held) {
      throw new CapacityBelowVehiclesHeldException(code, newCapacity, held);
    }
    return new Hub(id, code, name, position, newCapacity);
  }

  private static void requireValidCapacity(int capacity) {
    if (capacity < MIN_CAPACITY) {
      throw new IllegalArgumentException(
          "A hub's capacity must be at least %d: %d".formatted(MIN_CAPACITY, capacity));
    }
  }

  private static void requireValidHeld(int held) {
    if (held < 0) {
      throw new IllegalArgumentException("Vehicles held cannot be negative: " + held);
    }
  }
}
