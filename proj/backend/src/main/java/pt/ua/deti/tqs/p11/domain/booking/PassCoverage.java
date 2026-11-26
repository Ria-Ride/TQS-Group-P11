package pt.ua.deti.tqs.p11.domain.booking;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * What a booking needs to know about one of the tourist's Passes (BR4): when it is valid and which
 * vehicle types its pass type allows. Built from a Pass and its PassType, so the booking rules do
 * not depend on how passes are stored.
 */
public record PassCoverage(
    UUID passId, Instant validFrom, Instant validTo, Set<VehicleType> vehicleTypes) {

  public PassCoverage {
    Objects.requireNonNull(passId, "passId");
    Objects.requireNonNull(validFrom, "validFrom");
    Objects.requireNonNull(validTo, "validTo");
    Objects.requireNonNull(vehicleTypes, "vehicleTypes");
    if (vehicleTypes.isEmpty()) {
      throw new IllegalArgumentException("A pass allows at least one vehicle type");
    }
    if (!validTo.isAfter(validFrom)) {
      throw new IllegalArgumentException(
          "A pass must end after it starts: %s to %s".formatted(validFrom, validTo));
    }
    vehicleTypes = Set.copyOf(EnumSet.copyOf(vehicleTypes));
  }

  /** From validFrom included to validTo excluded, as the Pass was sold (BR8). */
  public boolean isValidAt(Instant at) {
    return !at.isBefore(validFrom) && at.isBefore(validTo);
  }

  public boolean allows(VehicleType type) {
    return vehicleTypes.contains(type);
  }

  /** A ride may end exactly when the Pass ends. */
  public boolean coversUntil(Instant end) {
    return !end.isAfter(validTo);
  }
}