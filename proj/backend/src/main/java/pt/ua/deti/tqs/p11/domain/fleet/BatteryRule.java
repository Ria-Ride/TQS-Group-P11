package pt.ua.deti.tqs.p11.domain.fleet;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * BR1: the battery a ride needs. It is a floor, plus a share for the ride when its duration is
 * known: every started block of 30 minutes costs a number of percent that depends on the vehicle
 * type (a bigger battery drains fewer percent in the same time). For example, with a floor of 20%
 * and 5% per block, 30 minutes need 25% and 31 minutes need 30%.
 *
 * <p>The result is not capped at 100%: a ride longer than a full battery can give needs more than
 * 100%, so no vehicle qualifies, which is the honest answer.
 */
public record BatteryRule(BatteryLevel floor, Map<VehicleType, Integer> percentPerBlock) {

  /** Length of one block of the ride. */
  public static final int BLOCK_MINUTES = 30;

  /** Longest ride that can be asked for: one day. */
  public static final int MAX_MINUTES = 24 * 60;

  public BatteryRule {
    Objects.requireNonNull(floor, "floor");
    Objects.requireNonNull(percentPerBlock, "percentPerBlock");
    Map<VehicleType, Integer> rates = new EnumMap<>(VehicleType.class);
    rates.putAll(percentPerBlock);
    for (VehicleType type : VehicleType.values()) {
      Integer rate = rates.get(type);
      if (rate == null) {
        throw new IllegalArgumentException("No battery rate for vehicle type " + type);
      }
      if (rate < 0) {
        throw new IllegalArgumentException(
            "The battery rate of %s cannot be negative: %d".formatted(type, rate));
      }
    }
    percentPerBlock = Map.copyOf(rates);
  }

  /** What a ride needs when its duration is not given. */
  public int floorPercent() {
    return floor.percent();
  }

  /**
   * The battery, in percent, needed to ride this type of vehicle for the minutes.
   *
   * @throws IllegalArgumentException if minutes is not from 1 to MAX_MINUTES
   */
  public int requiredPercent(VehicleType type, int minutes) {
    checkMinutes(minutes);
    int blocks = (minutes + BLOCK_MINUTES - 1) / BLOCK_MINUTES;
    return floor.percent() + blocks * percentPerBlock.get(type);
  }

  public static void checkMinutes(int minutes) {
    if (minutes < 1 || minutes > MAX_MINUTES) {
      throw new IllegalArgumentException(
          "The duration must be from 1 to %d minutes: %d".formatted(MAX_MINUTES, minutes));
    }
  }
}
