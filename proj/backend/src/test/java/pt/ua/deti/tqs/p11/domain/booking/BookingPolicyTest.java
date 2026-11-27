package pt.ua.deti.tqs.p11.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.rules.RulesOfUse;
import pt.ua.deti.tqs.p11.support.TestRules;

/**
 * Whether a booking can be created (BR1, BR2, BR4, BR5, BR6), for immediate use (US04) and later
 * for future bookings (US05). The rules of use are given (TestRules), so these tests do not depend
 * on the seeded version. When several rules fail, the first one in this order is reported:
 * duration, vehicle, Pass, overlap.
 */
class BookingPolicyTest {

  private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
  private static final UUID ANA = UUID.randomUUID();
  private static final UUID HUB = UUID.randomUUID();
  private static final UUID OTHER_BIKE = UUID.randomUUID();

  /**
   * Longest booking 4 hours; BR1: a 20% floor plus 5% per started 30 minutes, for both types;
   * deposits 30.00 (E_BIKE) and 50.00 (E_SCOOTER).
   */
  private static final RulesOfUse RULES = TestRules.bookingRules();

  private final BookingPolicy policy = new BookingPolicy(RULES);

  // ------------------------------------------------------------- fixtures

  private static Vehicle vehicle(VehicleType type, int battery) {
    return Vehicle.register(UUID.randomUUID(), FleetCode.of("veh-0001"), type, HUB)
        .activate()
        .withBattery(BatteryLevel.of(battery));
  }

  private static Vehicle bike() {
    return vehicle(VehicleType.E_BIKE, 100);
  }

  private static PassCoverage pass(Instant from, Instant to, VehicleType... types) {
    return new PassCoverage(UUID.randomUUID(), from, to, Set.of(types));
  }

  /** A day pass bought an hour ago that allows both types. */
  private static PassCoverage dayPass() {
    Instant from = NOW.minus(Duration.ofHours(1));
    return pass(from, from.plus(Duration.ofDays(1)), VehicleType.E_BIKE, VehicleType.E_SCOOTER);
  }

  private static Instant in(int minutes) {
    return NOW.plus(Duration.ofMinutes(minutes));
  }

  private static BookingTerms otherBikeTerms(Instant start, Instant end) {
    return new BookingTerms(ANA, OTHER_BIKE, UUID.randomUUID(), HUB, start, end);
  }

  private static Booking active(Instant start, Instant end) {
    return Booking.startNow(
        UUID.randomUUID(),
        otherBikeTerms(start, end),
        BookingRules.of(RULES, VehicleType.E_BIKE),
        "au_1");
  }

  private static Booking reserved(Instant start, Instant end) {
    return Booking.reserve(
        UUID.randomUUID(),
        otherBikeTerms(start, end),
        BookingRules.of(RULES, VehicleType.E_BIKE),
        "au_2");
  }

  /** A ride of the bike from now, with the day pass and no other booking. */
  private PassCoverage ride(int minutes) {
    return policy.check(bike(), NOW, in(minutes), List.of(dayPass()), List.of());
  }

  private static void assertRefused(ThrowingCallable check, BookingRefusal reason) {
    assertThatThrownBy(check)
        .isInstanceOfSatisfying(
            BookingRefusedException.class, e -> assertThat(e.reason()).isEqualTo(reason));
  }

  // ------------------------------------------------------------- a valid booking

  /** BR21: a new booking keeps the deposit of its type and the shared rules of this version. */
  @ParameterizedTest(name = "{0}")
  @EnumSource(VehicleType.class)
  void bookingRules_areThoseOfTheTypeInTheRulesChecked(VehicleType type) {
    assertThat(policy.bookingRules(type)).isEqualTo(BookingRules.of(RULES, type));
  }

  @Test
  void check_validRequest_returnsThePassThatCoversIt() {
    PassCoverage pass = dayPass();

    assertThat(policy.check(bike(), NOW, in(60), List.of(pass), List.of())).isEqualTo(pass);
  }

  // ------------------------------------------------------------- BR5: duration

  @ParameterizedTest(name = "{0} min: accepted")
  @CsvSource({"1", "60", "240"})
  void check_durationFromOneMinuteToTheMaximum_isAccepted(int minutes) {
    assertThat(ride(minutes)).isNotNull();
  }

  @ParameterizedTest(name = "{0} min: refused")
  @CsvSource({"0", "241"})
  void check_durationOfZeroOrAboveTheMaximum_isRefused(int minutes) {
    assertRefused(() -> ride(minutes), BookingRefusal.DURATION_NOT_ALLOWED);
  }

  /** The limit is exact: one second above four hours is already too long. */
  @Test
  void check_oneSecondAboveTheMaximum_isRefused() {
    Instant end = in(240).plusSeconds(1);

    assertRefused(
        () -> policy.check(bike(), NOW, end, List.of(dayPass()), List.of()),
        BookingRefusal.DURATION_NOT_ALLOWED);
  }

  @Test
  void check_endBeforeTheStart_isRefused() {
    Instant end = NOW.minusSeconds(60);

    assertRefused(
        () -> policy.check(bike(), NOW, end, List.of(dayPass()), List.of()),
        BookingRefusal.DURATION_NOT_ALLOWED);
  }

  // ------------------------------------------------------------- BR2, BR1: the vehicle

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = VehicleState.class,
      names = {"DEACTIVATED", "RESERVED", "MAINTENANCE"})
  void check_vehicleNotAvailable_isRefused(VehicleState state) {
    Vehicle vehicle =
        new Vehicle(
            UUID.randomUUID(),
            FleetCode.of("veh-0002"),
            VehicleType.E_BIKE,
            state,
            HUB,
            BatteryLevel.of(100));

    assertRefused(
        () -> policy.check(vehicle, NOW, in(60), List.of(dayPass()), List.of()),
        BookingRefusal.VEHICLE_NOT_AVAILABLE);
  }

  /** BR1: 60 minutes need 20% + 2 x 5% = 30%. */
  @ParameterizedTest(name = "{0}% for 60 min: {1}")
  @CsvSource({"29, false", "30, true"})
  void check_batteryForThePlannedRide(int battery, boolean accepted) {
    Vehicle vehicle = vehicle(VehicleType.E_BIKE, battery);
    ThrowingCallable check =
        () -> policy.check(vehicle, NOW, in(60), List.of(dayPass()), List.of());

    if (accepted) {
      assertThat(policy.check(vehicle, NOW, in(60), List.of(dayPass()), List.of())).isNotNull();
    } else {
      assertRefused(check, BookingRefusal.NOT_ENOUGH_BATTERY);
    }
  }

  /** A started minute counts as a minute: 30 min and 1 s need the battery of 31 minutes. */
  @Test
  void check_startedMinuteCountsForTheBattery() {
    Vehicle vehicle = vehicle(VehicleType.E_BIKE, 25);
    Instant end = in(30).plusSeconds(1);

    assertRefused(
        () -> policy.check(vehicle, NOW, end, List.of(dayPass()), List.of()),
        BookingRefusal.NOT_ENOUGH_BATTERY);
  }

  @Test
  void check_vehicleWithNoBatteryReading_isRefused() {
    Vehicle vehicle =
        Vehicle.register(UUID.randomUUID(), FleetCode.of("veh-0003"), VehicleType.E_BIKE, HUB)
            .activate();

    assertRefused(
        () -> policy.check(vehicle, NOW, in(60), List.of(dayPass()), List.of()),
        BookingRefusal.NOT_ENOUGH_BATTERY);
  }

  // ------------------------------------------------------------- BR4: the Pass

  @Test
  void check_noPass_isRefused() {
    assertRefused(
        () -> policy.check(bike(), NOW, in(60), List.of(), List.of()),
        BookingRefusal.NO_VALID_PASS);
  }

  @Test
  void check_expiredPass_isRefused() {
    PassCoverage expired =
        pass(NOW.minus(Duration.ofDays(1)), NOW, VehicleType.E_BIKE, VehicleType.E_SCOOTER);

    assertRefused(
        () -> policy.check(bike(), NOW, in(60), List.of(expired), List.of()),
        BookingRefusal.NO_VALID_PASS);
  }

  @Test
  void check_passThatStartsLater_isRefused() {
    PassCoverage later = pass(in(1), in(60 * 24), VehicleType.E_BIKE);

    assertRefused(
        () -> policy.check(bike(), NOW, in(60), List.of(later), List.of()),
        BookingRefusal.NO_VALID_PASS);
  }

  @Test
  void check_passThatStartsNow_isAccepted() {
    PassCoverage fromNow = pass(NOW, in(60 * 24), VehicleType.E_BIKE);

    assertThat(policy.check(bike(), NOW, in(60), List.of(fromNow), List.of())).isEqualTo(fromNow);
  }

  /** An E_SCOOTER with an E_BIKE-only Pass. */
  @Test
  void check_passThatDoesNotAllowTheType_isRefused() {
    PassCoverage bikesOnly = pass(NOW.minus(Duration.ofHours(1)), in(60 * 23), VehicleType.E_BIKE);
    Vehicle scooter = vehicle(VehicleType.E_SCOOTER, 100);

    assertRefused(
        () -> policy.check(scooter, NOW, in(60), List.of(bikesOnly), List.of()),
        BookingRefusal.PASS_DOES_NOT_ALLOW_TYPE);
  }

  /** The ride must end by the Pass's validTo: exactly at it is accepted, a minute later not. */
  @ParameterizedTest(name = "{0} min with a Pass ending in 60 min: {1}")
  @CsvSource({"60, true", "61, false"})
  void check_rideMustEndBeforeThePassDoes(int minutes, boolean accepted) {
    PassCoverage endsSoon = pass(NOW.minus(Duration.ofHours(23)), in(60), VehicleType.E_BIKE);
    ThrowingCallable check =
        () -> policy.check(bike(), NOW, in(minutes), List.of(endsSoon), List.of());

    if (accepted) {
      assertThat(policy.check(bike(), NOW, in(minutes), List.of(endsSoon), List.of()))
          .isEqualTo(endsSoon);
    } else {
      assertRefused(check, BookingRefusal.RIDE_ENDS_AFTER_PASS);
    }
  }

  @Test
  void check_severalPassesCoverTheRide_takesTheOneThatEndsLast() {
    PassCoverage day = pass(NOW.minus(Duration.ofHours(1)), in(60 * 23), VehicleType.E_BIKE);
    PassCoverage multiDay = pass(NOW.minus(Duration.ofHours(1)), in(60 * 71), VehicleType.E_BIKE);

    assertThat(policy.check(bike(), NOW, in(60), List.of(day, multiDay), List.of()))
        .isEqualTo(multiDay);
  }

  /** A Pass that fails one rule does not hide another Pass that covers the ride. */
  @Test
  void check_onePassFailsAndAnotherCovers_takesTheOneThatCovers() {
    PassCoverage scootersOnly =
        pass(NOW.minus(Duration.ofHours(1)), in(60 * 23), VehicleType.E_SCOOTER);
    PassCoverage endsSoon = pass(NOW.minus(Duration.ofHours(23)), in(30), VehicleType.E_BIKE);
    PassCoverage covers = pass(NOW.minus(Duration.ofHours(1)), in(60 * 23), VehicleType.E_BIKE);

    assertThat(
            policy.check(bike(), NOW, in(60), List.of(scootersOnly, endsSoon, covers), List.of()))
        .isEqualTo(covers);
  }

  // ------------------------------------------------------------- BR6: overlap

  /** One ride at a time: an Active booking blocks until it is completed, even in overtime. */
  @Test
  void check_activeBookingOfTheTourist_isRefused() {
    Booking riding = active(NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)));

    assertRefused(
        () -> policy.check(bike(), NOW, in(60), List.of(dayPass()), List.of(riding)),
        BookingRefusal.OVERLAPPING_BOOKING);
  }

  @Test
  void check_reservedBookingThatOverlaps_isRefused() {
    Booking later = reserved(in(30), in(90));

    assertRefused(
        () -> policy.check(bike(), NOW, in(60), List.of(dayPass()), List.of(later)),
        BookingRefusal.OVERLAPPING_BOOKING);
  }

  /** Back-to-back periods do not overlap: one may start exactly when the other ends. */
  @Test
  void check_reservedBookingThatStartsWhenThisOneEnds_isAccepted() {
    Booking next = reserved(in(60), in(120));

    assertThat(policy.check(bike(), NOW, in(60), List.of(dayPass()), List.of(next))).isNotNull();
  }

  @Test
  void check_reservedBookingThatEndsWhenThisOneStarts_isAccepted() {
    Booking previous = reserved(NOW.minus(Duration.ofHours(1)), NOW);

    assertThat(policy.check(bike(), NOW, in(60), List.of(dayPass()), List.of(previous)))
        .isNotNull();
  }

  @Test
  void check_bookingsThatAreOver_doNotBlock() {
    Booking completed = active(NOW.minus(Duration.ofHours(2)), in(30)).complete(HUB, NOW);
    Booking cancelled = reserved(in(10), in(50)).cancel("plans changed");
    Booking expired = reserved(in(20), in(40)).expire();

    assertThat(
            policy.check(
                bike(), NOW, in(60), List.of(dayPass()), List.of(completed, cancelled, expired)))
        .isNotNull();
  }

  // ------------------------------------------------------------- which rule is reported first

  @Test
  void check_durationIsCheckedBeforeTheVehicle() {
    Vehicle flat = vehicle(VehicleType.E_BIKE, 0);

    assertRefused(
        () -> policy.check(flat, NOW, in(241), List.of(dayPass()), List.of()),
        BookingRefusal.DURATION_NOT_ALLOWED);
  }

  @Test
  void check_vehicleIsCheckedBeforeThePass() {
    Vehicle flat = vehicle(VehicleType.E_BIKE, 0);

    assertRefused(
        () -> policy.check(flat, NOW, in(60), List.of(), List.of()),
        BookingRefusal.NOT_ENOUGH_BATTERY);
  }

  @Test
  void check_passIsCheckedBeforeTheOverlap() {
    Booking riding = active(NOW.minus(Duration.ofMinutes(10)), in(50));

    assertRefused(
        () -> policy.check(bike(), NOW, in(60), List.of(), List.of(riding)),
        BookingRefusal.NO_VALID_PASS);
  }
}
