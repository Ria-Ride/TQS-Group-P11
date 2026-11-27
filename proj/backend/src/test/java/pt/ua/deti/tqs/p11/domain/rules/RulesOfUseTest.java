package pt.ua.deti.tqs.p11.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * The rules of use the client sets (US14, BR21). Every value is checked when the rules are made, so
 * an invalid set of rules cannot exist; a change makes a new version and never edits the old one.
 */
class RulesOfUseTest {

  private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");
  private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000066");

  private static Map<VehicleType, VehicleTypeRules> perType() {
    Map<VehicleType, VehicleTypeRules> rules = new EnumMap<>(VehicleType.class);
    rules.put(VehicleType.E_BIKE, new VehicleTypeRules(Money.of("20"), 5));
    rules.put(VehicleType.E_SCOOTER, new VehicleTypeRules(Money.of("30"), 8));
    return rules;
  }

  private static RulesOfUse version1() {
    return new RulesOfUse(
        1,
        T0,
        null,
        Duration.ofHours(8),
        BatteryLevel.of(20),
        Duration.ofMinutes(15),
        Duration.ofMinutes(10),
        Duration.ofMinutes(5),
        Duration.ofMinutes(15),
        Money.of("1.50"),
        perType());
  }

  private static RulesChange change() {
    return new RulesChange(
        Duration.ofHours(4),
        BatteryLevel.of(25),
        Duration.ofMinutes(20),
        Duration.ofMinutes(12),
        Duration.ofMinutes(0),
        Duration.ofMinutes(30),
        Money.of("2.00"),
        Map.of(VehicleType.E_BIKE, 4, VehicleType.E_SCOOTER, 9));
  }

  // ------------------------------------------------------------- what the rules give

  @Test
  void batteryRule_isTheMinimumAndTheRatePerVehicleType() {
    var rule = version1().batteryRule();

    assertThat(rule.floorPercent()).isEqualTo(20);
    assertThat(rule.requiredPercent(VehicleType.E_BIKE, 60)).isEqualTo(30);
    assertThat(rule.requiredPercent(VehicleType.E_SCOOTER, 60)).isEqualTo(36);
  }

  @Test
  void deposit_isTheOneOfTheVehicleType() {
    assertThat(version1().deposit(VehicleType.E_BIKE)).isEqualTo(Money.of("20.00"));
    assertThat(version1().deposit(VehicleType.E_SCOOTER)).isEqualTo(Money.of("30.00"));
  }

  // ------------------------------------------------------------- BR21: limits

  /** BR21: the longest booking is more than 0 and at most 24 hours. */
  @Test
  void maxBookingDuration_between0Exclusive24HoursInclusive_isAccepted() {
    assertThat(withMaxBooking(Duration.ofMinutes(1)).maxBookingDuration())
        .isEqualTo(Duration.ofMinutes(1));
    assertThat(withMaxBooking(Duration.ofHours(24)).maxBookingDuration())
        .isEqualTo(Duration.ofHours(24));
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, 1441, 100000})
  void maxBookingDuration_zeroNegativeOrOver24Hours_isRefused(long minutes) {
    assertThatThrownBy(() -> withMaxBooking(Duration.ofMinutes(minutes)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static RulesOfUse withMaxBooking(Duration max) {
    return new RulesOfUse(
        1,
        T0,
        null,
        max,
        BatteryLevel.of(20),
        Duration.ofMinutes(15),
        Duration.ofMinutes(10),
        Duration.ofMinutes(5),
        Duration.ofMinutes(15),
        Money.of("1.50"),
        perType());
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -5, 1441})
  void startWindowRepositioningWindowAndOvertimeBlock_mustBePositiveAndAtMost24Hours(long minutes) {
    Duration bad = Duration.ofMinutes(minutes);
    assertThatThrownBy(() -> revise(version1(), startWindow(bad)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> revise(version1(), repositioning(bad)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> revise(version1(), overtimeBlock(bad)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void overtimeGrace_canBeZero_butNotNegativeOrOver24Hours() {
    assertThat(revise(version1(), grace(Duration.ZERO)).overtimeGrace()).isEqualTo(Duration.ZERO);
    assertThatThrownBy(() -> revise(version1(), grace(Duration.ofMinutes(-1))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> revise(version1(), grace(Duration.ofMinutes(1441))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void batteryRate_perVehicleType_isFrom0To100() {
    assertThatThrownBy(() -> new VehicleTypeRules(Money.of("1"), -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new VehicleTypeRules(Money.of("1"), 101))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new VehicleTypeRules(Money.of("1"), 0).batteryPercentPer30Minutes()).isZero();
  }

  @Test
  void everyVehicleTypeNeedsItsRules() {
    Map<VehicleType, VehicleTypeRules> onlyBikes =
        Map.of(VehicleType.E_BIKE, new VehicleTypeRules(Money.of("20"), 5));

    assertThatThrownBy(() ->
            new RulesOfUse(
                1,
                T0,
                null,
                Duration.ofHours(8),
                BatteryLevel.of(20),
                Duration.ofMinutes(15),
                Duration.ofMinutes(10),
                Duration.ofMinutes(5),
                Duration.ofMinutes(15),
                Money.of("1"),
                onlyBikes))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** The rules are kept in whole minutes, so a value with seconds would be silently cut. */
  @Test
  void durations_mustBeWholeMinutes() {
    assertThatThrownBy(() -> withMaxBooking(Duration.ofSeconds(90)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> revise(version1(), grace(Duration.ofSeconds(30))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void version_startsAtOne() {
    assertThatThrownBy(() -> withVersion(0)).isInstanceOf(IllegalArgumentException.class);
  }

  private static RulesOfUse withVersion(int version) {
    return new RulesOfUse(
        version,
        T0,
        null,
        Duration.ofHours(8),
        BatteryLevel.of(20),
        Duration.ofMinutes(15),
        Duration.ofMinutes(10),
        Duration.ofMinutes(5),
        Duration.ofMinutes(15),
        Money.of("1"),
        perType());
  }

  // ------------------------------------------------------------- BR21: a change is a new version

  @Test
  void revised_isTheNextVersion_withTheNewValuesAndWhoMadeIt() {
    RulesOfUse next = version1().revised(T0.plusSeconds(60), MANAGER, change());

    assertThat(next.version()).isEqualTo(2);
    assertThat(next.createdAt()).isEqualTo(T0.plusSeconds(60));
    assertThat(next.createdBy()).isEqualTo(MANAGER);
    assertThat(next.maxBookingDuration()).isEqualTo(Duration.ofHours(4));
    assertThat(next.minBattery()).isEqualTo(BatteryLevel.of(25));
    assertThat(next.startWindow()).isEqualTo(Duration.ofMinutes(20));
    assertThat(next.overtimeRatePerBlock()).isEqualTo(Money.of("2.00"));
    assertThat(next.batteryRule().requiredPercent(VehicleType.E_BIKE, 30)).isEqualTo(29);
    assertThat(next.batteryRule().requiredPercent(VehicleType.E_SCOOTER, 30)).isEqualTo(34);
  }

  @Test
  void revised_keepsTheDeposits_becauseTheyAreChangedOnTheirOwn() {
    RulesOfUse next = version1().revised(T0.plusSeconds(60), MANAGER, change());

    assertThat(next.deposit(VehicleType.E_BIKE)).isEqualTo(Money.of("20.00"));
    assertThat(next.deposit(VehicleType.E_SCOOTER)).isEqualTo(Money.of("30.00"));
  }

  @Test
  void revised_neverChangesTheOldVersion() {
    RulesOfUse old = version1();

    old.revised(T0.plusSeconds(60), MANAGER, change());

    assertThat(old.version()).isEqualTo(1);
    assertThat(old.maxBookingDuration()).isEqualTo(Duration.ofHours(8));
    assertThat(old.minBattery()).isEqualTo(BatteryLevel.of(20));
  }

  @Test
  void withDeposit_isTheNextVersion_withOneDepositChangedAndTheRestKept() {
    RulesOfUse next =
        version1().withDeposit(T0.plusSeconds(60), MANAGER, VehicleType.E_SCOOTER, Money.of("45"));

    assertThat(next.version()).isEqualTo(2);
    assertThat(next.createdBy()).isEqualTo(MANAGER);
    assertThat(next.deposit(VehicleType.E_SCOOTER)).isEqualTo(Money.of("45.00"));
    assertThat(next.deposit(VehicleType.E_BIKE)).isEqualTo(Money.of("20.00"));
    assertThat(next.maxBookingDuration()).isEqualTo(Duration.ofHours(8));
    assertThat(next.batteryRule().requiredPercent(VehicleType.E_SCOOTER, 30)).isEqualTo(28);
  }

  @Test
  void revised_withAnInvalidValue_isRefused() {
    RulesChange bad =
        new RulesChange(
            Duration.ofHours(25),
            BatteryLevel.of(25),
            Duration.ofMinutes(20),
            Duration.ofMinutes(12),
            Duration.ZERO,
            Duration.ofMinutes(30),
            Money.of("2"),
            Map.of(VehicleType.E_BIKE, 4, VehicleType.E_SCOOTER, 9));

    assertThatThrownBy(() -> version1().revised(T0.plusSeconds(60), MANAGER, bad))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ------------------------------------------------------------- helpers

  private static RulesChange startWindow(Duration value) {
    return new RulesChange(
        Duration.ofHours(4),
        BatteryLevel.of(25),
        value,
        Duration.ofMinutes(12),
        Duration.ZERO,
        Duration.ofMinutes(30),
        Money.of("2"),
        Map.of(VehicleType.E_BIKE, 4, VehicleType.E_SCOOTER, 9));
  }

  private static RulesChange repositioning(Duration value) {
    return new RulesChange(
        Duration.ofHours(4),
        BatteryLevel.of(25),
        Duration.ofMinutes(20),
        value,
        Duration.ZERO,
        Duration.ofMinutes(30),
        Money.of("2"),
        Map.of(VehicleType.E_BIKE, 4, VehicleType.E_SCOOTER, 9));
  }

  private static RulesChange overtimeBlock(Duration value) {
    return new RulesChange(
        Duration.ofHours(4),
        BatteryLevel.of(25),
        Duration.ofMinutes(20),
        Duration.ofMinutes(12),
        Duration.ZERO,
        value,
        Money.of("2"),
        Map.of(VehicleType.E_BIKE, 4, VehicleType.E_SCOOTER, 9));
  }

  private static RulesChange grace(Duration value) {
    return new RulesChange(
        Duration.ofHours(4),
        BatteryLevel.of(25),
        Duration.ofMinutes(20),
        Duration.ofMinutes(12),
        value,
        Duration.ofMinutes(30),
        Money.of("2"),
        Map.of(VehicleType.E_BIKE, 4, VehicleType.E_SCOOTER, 9));
  }

  private static RulesOfUse revise(RulesOfUse from, RulesChange change) {
    return from.revised(T0.plusSeconds(60), MANAGER, change);
  }
}
