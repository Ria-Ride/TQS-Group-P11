package pt.ua.deti.tqs.p11.domain.booking;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryRule;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * Decides whether a booking can be created, in one place for immediate use (US04) and future
 * bookings (US05). The rules are checked in this order, and the first that fails is reported:
 * duration (BR5), vehicle (BR2, then BR1), Pass (BR4), overlap with the tourist's bookings (BR6).
 * Time is given, never read: the caller takes it from its injected Clock.
 */
public final class BookingPolicy {

  private final UsageRules rules;
  private final BatteryRule battery;

  public BookingPolicy(UsageRules rules, BatteryRule battery) {
    this.rules = Objects.requireNonNull(rules, "rules");
    this.battery = Objects.requireNonNull(battery, "battery");
  }

  public UsageRules rules() {
    return rules;
  }

  /**
   * @param passes the tourist's Passes
   * @param bookingsOfTheAccount the tourist's bookings; only Reserved and Active ones count
   * @return the Pass that covers the booking: of those that do, the one that ends last
   * @throws BookingRefusedException with the first rule that fails
   */
  public PassCoverage check(
      Vehicle vehicle,
      Instant start,
      Instant end,
      List<PassCoverage> passes,
      List<Booking> bookingsOfTheAccount) {
    Objects.requireNonNull(vehicle, "vehicle");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    Objects.requireNonNull(passes, "passes");
    Objects.requireNonNull(bookingsOfTheAccount, "bookingsOfTheAccount");
    Duration duration = checkDuration(start, end);
    checkVehicle(vehicle, duration);
    PassCoverage pass = choosePass(passes, vehicle.type(), start, end);
    checkNoOverlap(bookingsOfTheAccount, start, end);
    return pass;
  }

  /** BR5: above 0 and at most maxBookingDuration, to the second. */
  private Duration checkDuration(Instant start, Instant end) {
    if (!end.isAfter(start)) {
      throw new BookingRefusedException(BookingRefusal.DURATION_NOT_ALLOWED);
    }
    Duration duration = Duration.between(start, end);
    if (duration.compareTo(rules.maxBookingDuration()) > 0) {
      throw new BookingRefusedException(BookingRefusal.DURATION_NOT_ALLOWED);
    }
    return duration;
  }

  /**
   * BR2, then BR1 with the planned duration; a started minute counts as a whole one. The duration
   * is at most one day here (UsageRules), which is what the battery rule accepts.
   */
  private void checkVehicle(Vehicle vehicle, Duration duration) {
    long minutes = duration.toMinutes();
    if (duration.minusMinutes(minutes).isPositive()) {
      minutes++;
    }
    switch (vehicle.bookability(battery, OptionalInt.of((int) minutes))) {
      case BOOKABLE -> {
        // nothing to refuse
      }
      case NOT_AVAILABLE -> throw new BookingRefusedException(BookingRefusal.VEHICLE_NOT_AVAILABLE);
      case NO_BATTERY_READING, LOW_BATTERY, INSUFFICIENT_FOR_DURATION ->
          throw new BookingRefusedException(BookingRefusal.NOT_ENOUGH_BATTERY);
    }
  }

  /**
   * BR4, narrowed step by step so the reason is the most useful one: valid at the start, then
   * allowing the type, then still valid at the end.
   */
  private static PassCoverage choosePass(
      List<PassCoverage> passes, VehicleType type, Instant start, Instant end) {
    List<PassCoverage> valid = passes.stream().filter(pass -> pass.isValidAt(start)).toList();
    if (valid.isEmpty()) {
      throw new BookingRefusedException(BookingRefusal.NO_VALID_PASS);
    }
    List<PassCoverage> allowing = valid.stream().filter(pass -> pass.allows(type)).toList();
    if (allowing.isEmpty()) {
      throw new BookingRefusedException(BookingRefusal.PASS_DOES_NOT_ALLOW_TYPE);
    }
    return allowing.stream()
        .filter(pass -> pass.coversUntil(end))
        .max(Comparator.comparing(PassCoverage::validTo))
        .orElseThrow(() -> new BookingRefusedException(BookingRefusal.RIDE_ENDS_AFTER_PASS));
  }

  /** BR6. */
  private static void checkNoOverlap(List<Booking> bookings, Instant start, Instant end) {
    boolean blocked =
        bookings.stream()
            .filter(booking -> booking.state().isOngoing())
            .anyMatch(booking -> blocks(booking, start, end));
    if (blocked) {
      throw new BookingRefusedException(BookingRefusal.OVERLAPPING_BOOKING);
    }
  }

  /**
   * One ride at a time: an Active booking blocks until it is completed, even after its planned end
   * (overtime). A Reserved one blocks only its planned period; back-to-back periods do not overlap.
   */
  private static boolean blocks(Booking booking, Instant start, Instant end) {
    if (booking.state() == BookingState.ACTIVE) {
      return true;
    }
    BookingTerms terms = booking.terms();
    return terms.plannedStart().isBefore(end) && start.isBefore(terms.plannedEnd());
  }
}