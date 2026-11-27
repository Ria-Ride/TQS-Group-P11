package pt.ua.deti.tqs.p11.domain.rules;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryRule;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * The rules of use the client sets (US14): longest booking, minimum battery, windows, overtime and
 * the deposit and battery rate of each vehicle type. Immutable and always valid. BR21: a change
 * never edits a version, it makes the next one ({@link #revised}, {@link #withDeposit}), so what a
 * booking was created with stays what it was.
 *
 * <p>Time values are durations; a window or block is more than zero and at most 24 hours, the grace
 * period may be zero, and the longest booking is more than zero and at most 24 hours.
 *
 * @param createdBy the account that made this version, or null for the first one, which is seeded
 */
public record RulesOfUse(
    int version,
    Instant createdAt,
    UUID createdBy,
    Duration maxBookingDuration,
    BatteryLevel minBattery,
    Duration startWindow,
    Duration repositioningWindow,
    Duration overtimeGrace,
    Duration overtimeBlock,
    Money overtimeRatePerBlock,
    Map<VehicleType, VehicleTypeRules> vehicleTypes) {

  /** BR21: no duration of the rules is longer than a day. */
  public static final Duration MAX_DURATION = Duration.ofHours(24);

  public RulesOfUse {
    if (version < 1) {
      throw new IllegalArgumentException("Versions start at 1: " + version);
    }
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(minBattery, "minBattery");
    Objects.requireNonNull(overtimeRatePerBlock, "overtimeRatePerBlock");
    Objects.requireNonNull(vehicleTypes, "vehicleTypes");
    requirePositiveUpToADay("maxBookingDuration", maxBookingDuration);
    requirePositiveUpToADay("startWindow", startWindow);
    requirePositiveUpToADay("repositioningWindow", repositioningWindow);
    requirePositiveUpToADay("overtimeBlock", overtimeBlock);
    Objects.requireNonNull(overtimeGrace, "overtimeGrace");
    requireWholeMinutes("overtimeGrace", overtimeGrace);
    if (overtimeGrace.isNegative() || overtimeGrace.compareTo(MAX_DURATION) > 0) {
      throw new IllegalArgumentException("overtimeGrace is from 0 to 24 hours: " + overtimeGrace);
    }
    Map<VehicleType, VehicleTypeRules> complete = new EnumMap<>(VehicleType.class);
    complete.putAll(vehicleTypes);
    for (VehicleType type : VehicleType.values()) {
      if (complete.get(type) == null) {
        throw new IllegalArgumentException("The rules have nothing for vehicle type " + type);
      }
    }
    vehicleTypes = Map.copyOf(complete);
  }

  private static void requireWholeMinutes(String field, Duration value) {
    if (value.toMillis() % Duration.ofMinutes(1).toMillis() != 0) {
      throw new IllegalArgumentException("%s is in whole minutes: %s".formatted(field, value));
    }
  }

  private static void requirePositiveUpToADay(String field, Duration value) {
    Objects.requireNonNull(value, field);
    requireWholeMinutes(field, value);
    if (value.isZero() || value.isNegative() || value.compareTo(MAX_DURATION) > 0) {
      throw new IllegalArgumentException(
          "%s is more than 0 and at most 24 hours: %s".formatted(field, value));
    }
  }

  /** BR1: the minimum battery and the rate of each vehicle type, as the search and booking use. */
  public BatteryRule batteryRule() {
    Map<VehicleType, Integer> rates = new EnumMap<>(VehicleType.class);
    vehicleTypes.forEach((type, rules) -> rates.put(type, rules.batteryPercentPer30Minutes()));
    return new BatteryRule(minBattery, rates);
  }

  /** The deposit held for a booking of this vehicle type. */
  public Money deposit(VehicleType type) {
    return vehicleTypes.get(type).deposit();
  }

  /**
   * The next version with the values of the change. The deposits stay as they are.
   *
   * @throws IllegalArgumentException if a value is outside its limits
   */
  public RulesOfUse revised(Instant at, UUID by, RulesChange change) {
    Objects.requireNonNull(change, "change");
    Map<VehicleType, VehicleTypeRules> next = new EnumMap<>(VehicleType.class);
    for (VehicleType type : VehicleType.values()) {
      Integer rate = change.batteryPercentPer30Minutes().get(type);
      if (rate == null) {
        throw new IllegalArgumentException(
            "The battery used per 30 minutes of " + type + " is needed");
      }
      next.put(type, new VehicleTypeRules(vehicleTypes.get(type).deposit(), rate));
    }
    return new RulesOfUse(
        version + 1,
        at,
        by,
        change.maxBookingDuration(),
        change.minBattery(),
        change.startWindow(),
        change.repositioningWindow(),
        change.overtimeGrace(),
        change.overtimeBlock(),
        change.overtimeRatePerBlock(),
        next);
  }

  /** The next version with the deposit of one vehicle type changed and everything else kept. */
  public RulesOfUse withDeposit(Instant at, UUID by, VehicleType type, Money deposit) {
    Map<VehicleType, VehicleTypeRules> next = new EnumMap<>(VehicleType.class);
    next.putAll(vehicleTypes);
    next.put(
        type, new VehicleTypeRules(deposit, vehicleTypes.get(type).batteryPercentPer30Minutes()));
    return new RulesOfUse(
        version + 1,
        at,
        by,
        maxBookingDuration,
        minBattery,
        startWindow,
        repositioningWindow,
        overtimeGrace,
        overtimeBlock,
        overtimeRatePerBlock,
        next);
  }
}
