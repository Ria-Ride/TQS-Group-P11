package pt.ua.deti.tqs.p11.domain.booking;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * What was booked: the account, the vehicle, the Pass that covers it, the hub where it starts and
 * the planned period. Whether these satisfy the rules of use (duration, Pass, battery, overlap) is
 * checked before the booking is created (BR1 to BR6), not here.
 */
public record BookingTerms(
    UUID accountId,
    UUID vehicleId,
    UUID passId,
    UUID startHubId,
    Instant plannedStart,
    Instant plannedEnd) {

  public BookingTerms {
    Objects.requireNonNull(accountId, "accountId");
    Objects.requireNonNull(vehicleId, "vehicleId");
    Objects.requireNonNull(passId, "passId");
    Objects.requireNonNull(startHubId, "startHubId");
    Objects.requireNonNull(plannedStart, "plannedStart");
    Objects.requireNonNull(plannedEnd, "plannedEnd");
    if (!plannedEnd.isAfter(plannedStart)) {
      throw new IllegalArgumentException(
          "A booking must end after it starts: %s to %s".formatted(plannedStart, plannedEnd));
    }
  }

  public Duration plannedDuration() {
    return Duration.between(plannedStart, plannedEnd);
  }
}
