package pt.ua.deti.tqs.p11.domain.pass;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * A kind of pass, such as DAY (1 day) or MULTI_DAY (3 days): its validity, base price and the
 * vehicle types it allows. Set by the client (US14); seeded until then.
 */
public record PassType(
    UUID id,
    String code,
    String name,
    int validityDays,
    Money basePrice,
    Set<VehicleType> vehicleTypes) {

  /** One day of validity is exactly 24 hours (BR8), whatever the clocks of the hotel do. */
  private static final Duration DAY = Duration.ofHours(24);

  public PassType {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(basePrice, "basePrice");
    Objects.requireNonNull(vehicleTypes, "vehicleTypes");
    if (code.isBlank()) {
      throw new IllegalArgumentException("A pass type needs a code");
    }
    if (validityDays < 1) {
      throw new IllegalArgumentException("A pass lasts at least one day: " + validityDays);
    }
    if (vehicleTypes.isEmpty()) {
      throw new IllegalArgumentException("A pass type allows at least one vehicle type");
    }
    vehicleTypes = Set.copyOf(EnumSet.copyOf(vehicleTypes));
  }

  /**
   * BR8: the end of a pass paid at {@code from}, validityDays x 24 hours later. Counted on the
   * timeline (Instant), not in calendar days, so a change of summer time does not shorten a pass.
   */
  public Instant validUntil(Instant from) {
    return from.plus(DAY.multipliedBy(validityDays));
  }

  /** BR9: the base price minus the discount of the stay's hotel, rounded half-up to the cent. */
  public Money priceWith(Stay stay) {
    return basePrice.minusPercent(stay.discountPercent());
  }
}
