package pt.ua.deti.tqs.p11.domain.booking;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * The client's current rules of use for bookings: the longest booking (BR5), the deposit per
 * vehicle type (BR11), the start window of a reservation (BR7) and the overtime parameters (BR12).
 * Seeded from configuration until the client edits them (US14). A new booking keeps a copy of the
 * ones that matter to it, {@link #forType}, so later changes do not affect it (BR21).
 */
public record UsageRules(
    Duration maxBookingDuration,
    Map<VehicleType, Money> deposits,
    Duration startWindow,
    Duration overtimeGrace,
    Duration overtimeBlock,
    Money overtimePricePerBlock) {

  /** US14: the longest booking a client can allow. */
  public static final Duration MAX_BOOKING_DURATION_LIMIT = Duration.ofHours(24);

  public UsageRules {
    Objects.requireNonNull(maxBookingDuration, "maxBookingDuration");
    Objects.requireNonNull(deposits, "deposits");
    if (maxBookingDuration.isNegative()
        || maxBookingDuration.isZero()
        || maxBookingDuration.compareTo(MAX_BOOKING_DURATION_LIMIT) > 0) {
      throw new IllegalArgumentException(
          "The longest booking must be above 0 and at most %s: %s"
              .formatted(MAX_BOOKING_DURATION_LIMIT, maxBookingDuration));
    }
    Map<VehicleType, Money> perType = new EnumMap<>(VehicleType.class);
    perType.putAll(deposits);
    for (VehicleType type : VehicleType.values()) {
      Money deposit = perType.get(type);
      if (deposit == null) {
        throw new IllegalArgumentException("No deposit for vehicle type " + type);
      }
      // The rules a booking keeps are checked now, when the client sets them, not at booking time.
      new BookingRules(deposit, startWindow, overtimeGrace, overtimeBlock, overtimePricePerBlock);
    }
    deposits = Map.copyOf(perType);
  }

  /** The rules a new booking of this vehicle type keeps (BR21). */
  public BookingRules forType(VehicleType type) {
    return new BookingRules(
        deposits.get(type), startWindow, overtimeGrace, overtimeBlock, overtimePricePerBlock);
  }
}