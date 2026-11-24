package pt.ua.deti.tqs.p11.domain.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The battery of a vehicle and whether it can be booked (US02): BR1 (minimum battery) and BR2 (only
 * Available vehicles). The minimum is a parameter, so these tests do not depend on the default.
 */
class VehicleBookabilityTest {

  private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
  private static final UUID HUB_03 = UUID.fromString("00000000-0000-0000-0000-000000000103");
  private static final UUID HUB_05 = UUID.fromString("00000000-0000-0000-0000-000000000105");
  private static final FleetCode CODE = FleetCode.of("bike-0009");
  private static final BatteryRule RULE_20 = rule(20, 5, 5);

  private static BatteryRule rule(int floor, int bikePercent, int scooterPercent) {
    return new BatteryRule(
        BatteryLevel.of(floor),
        Map.of(VehicleType.E_BIKE, bikePercent, VehicleType.E_SCOOTER, scooterPercent));
  }

  private static Vehicle availableBike() {
    return Vehicle.register(ID, CODE, VehicleType.E_BIKE, HUB_03).activate();
  }

  // ------------------------------------------------------------- the battery of a vehicle

  @Test
  void register_newVehicle_hasNoBatteryReadingYet() {
    assertThat(Vehicle.register(ID, CODE, VehicleType.E_BIKE, HUB_03).battery()).isEmpty();
  }

  @Test
  void withBattery_storesTheLevelAndChangesNothingElse() {
    Vehicle vehicle = availableBike().withBattery(BatteryLevel.of(64));

    assertThat(vehicle.battery()).contains(BatteryLevel.of(64));
    assertThat(vehicle.state()).isEqualTo(VehicleState.AVAILABLE);
    assertThat(vehicle.currentHub()).contains(HUB_03);
  }

  @Test
  void withBattery_newReading_replacesTheOldOne() {
    Vehicle vehicle =
        availableBike().withBattery(BatteryLevel.of(64)).withBattery(BatteryLevel.of(7));

    assertThat(vehicle.battery()).contains(BatteryLevel.of(7));
  }

  @Test
  void withBattery_noLevel_isRefused() {
    assertThatThrownBy(() -> availableBike().withBattery(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void transitions_keepTheLastBatteryReading() {
    Vehicle vehicle = availableBike().withBattery(BatteryLevel.of(64));

    assertThat(vehicle.deactivate().battery()).contains(BatteryLevel.of(64));
    assertThat(vehicle.startMove().battery()).contains(BatteryLevel.of(64));
    assertThat(vehicle.startMove().confirmArrival(HUB_05).battery()).contains(BatteryLevel.of(64));
  }

  // ------------------------------------------------------------- BR1: minimum battery

  /** 19% is refused and 20% accepted when the minimum is 20%; the same edge for another minimum. */
  @ParameterizedTest(name = "minimum {0}%: battery {1}% is {2}")
  @CsvSource({
    "20, 19, LOW_BATTERY",
    "20, 20, BOOKABLE",
    "20, 21, BOOKABLE",
    "20, 0, LOW_BATTERY",
    "20, 100, BOOKABLE",
    "50, 49, LOW_BATTERY",
    "50, 50, BOOKABLE",
    "0, 0, BOOKABLE"
  })
  void bookability_availableVehicle_isDecidedByTheBatteryAgainstTheMinimum(
      int minimum, int battery, Bookability expected) {
    Vehicle vehicle = availableBike().withBattery(BatteryLevel.of(battery));

    assertThat(vehicle.bookability(rule(minimum, 5, 5), OptionalInt.empty())).isEqualTo(expected);
  }

  @Test
  void bookability_availableVehicleWithoutAReading_isNotBookable() {
    assertThat(availableBike().bookability(RULE_20, OptionalInt.empty()))
        .isEqualTo(Bookability.NO_BATTERY_READING);
  }

  // ------------------------------------------------------------- BR2: only Available

  @ParameterizedTest
  @EnumSource(
      value = VehicleState.class,
      names = {"DEACTIVATED", "RESERVED", "IN_USE", "MAINTENANCE", "IN_TRANSIT", "RETIRED"})
  void bookability_vehicleNotAvailable_isNotBookableEvenWithAFullBattery(VehicleState state) {
    Vehicle vehicle =
        new Vehicle(ID, CODE, VehicleType.E_BIKE, state, state.isAtHub() ? HUB_03 : null)
            .withBattery(BatteryLevel.of(100));

    assertThat(vehicle.bookability(RULE_20, OptionalInt.empty()))
        .isEqualTo(Bookability.NOT_AVAILABLE);
  }

  /** Two reasons apply; the state is reported, because a low battery would not be the cause. */
  @Test
  void bookability_reservedVehicleWithLowBattery_reportsTheStateNotTheBattery() {
    Vehicle vehicle =
        new Vehicle(ID, CODE, VehicleType.E_BIKE, VehicleState.RESERVED, HUB_03)
            .withBattery(BatteryLevel.of(5));

    assertThat(vehicle.bookability(RULE_20, OptionalInt.empty()))
        .isEqualTo(Bookability.NOT_AVAILABLE);
  }

  // ------------------------------------------------------------- BR1 with a duration

  private static Vehicle availableWithBattery(VehicleType type, int percent) {
    return new Vehicle(ID, CODE, type, VehicleState.AVAILABLE, HUB_03)
        .withBattery(BatteryLevel.of(percent));
  }

  /** 30 minutes need 20% + 5% = 25%; 31 minutes need 30%. Below that the ride is not offered. */
  @ParameterizedTest(name = "{0} minutes, battery {1}% is {2}")
  @CsvSource({
    "30, 24, INSUFFICIENT_FOR_DURATION",
    "30, 25, BOOKABLE",
    "31, 29, INSUFFICIENT_FOR_DURATION",
    "31, 30, BOOKABLE",
    "120, 39, INSUFFICIENT_FOR_DURATION",
    "120, 40, BOOKABLE"
  })
  void bookability_withADuration_needsTheFloorPlusTheRidesShare(
      int minutes, int battery, Bookability expected) {
    Vehicle vehicle = availableWithBattery(VehicleType.E_BIKE, battery);

    assertThat(vehicle.bookability(RULE_20, OptionalInt.of(minutes))).isEqualTo(expected);
  }

  /** Below the floor the vehicle is refused whatever the ride: the floor is the first reason. */
  @Test
  void bookability_belowTheFloorWithADuration_reportsLowBattery() {
    Vehicle vehicle = availableWithBattery(VehicleType.E_BIKE, 19);

    assertThat(vehicle.bookability(RULE_20, OptionalInt.of(30))).isEqualTo(Bookability.LOW_BATTERY);
  }

  @Test
  void bookability_withADuration_usesTheRateOfTheVehicleType() {
    BatteryRule rule = rule(20, 5, 8);

    assertThat(availableWithBattery(VehicleType.E_BIKE, 27).bookability(rule, OptionalInt.of(30)))
        .isEqualTo(Bookability.BOOKABLE);
    assertThat(
            availableWithBattery(VehicleType.E_SCOOTER, 27).bookability(rule, OptionalInt.of(30)))
        .isEqualTo(Bookability.INSUFFICIENT_FOR_DURATION);
    assertThat(
            availableWithBattery(VehicleType.E_SCOOTER, 28).bookability(rule, OptionalInt.of(30)))
        .isEqualTo(Bookability.BOOKABLE);
  }

  @Test
  void bookability_rideLongerThanAnyBatteryCanGive_isNotBookableEvenWhenFull() {
    Vehicle full = availableWithBattery(VehicleType.E_BIKE, 100);

    assertThat(full.bookability(RULE_20, OptionalInt.of(BatteryRule.MAX_MINUTES)))
        .isEqualTo(Bookability.INSUFFICIENT_FOR_DURATION);
  }

  @Test
  void bookability_withADuration_stateAndMissingReadingStillComeFirst() {
    Vehicle reserved =
        new Vehicle(ID, CODE, VehicleType.E_BIKE, VehicleState.RESERVED, HUB_03)
            .withBattery(BatteryLevel.of(100));
    Vehicle noReading = new Vehicle(ID, CODE, VehicleType.E_BIKE, VehicleState.AVAILABLE, HUB_03);

    assertThat(reserved.bookability(RULE_20, OptionalInt.of(30)))
        .isEqualTo(Bookability.NOT_AVAILABLE);
    assertThat(noReading.bookability(RULE_20, OptionalInt.of(30)))
        .isEqualTo(Bookability.NO_BATTERY_READING);
  }

  @Test
  void bookability_durationOutsideTheLimits_isRefused() {
    Vehicle vehicle = availableWithBattery(VehicleType.E_BIKE, 100);

    assertThatThrownBy(() -> vehicle.bookability(RULE_20, OptionalInt.of(0)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void isBookable_isTrueOnlyForBOOKABLE() {
    assertThat(Bookability.BOOKABLE.isBookable()).isTrue();
    assertThat(Bookability.NOT_AVAILABLE.isBookable()).isFalse();
    assertThat(Bookability.LOW_BATTERY.isBookable()).isFalse();
    assertThat(Bookability.INSUFFICIENT_FOR_DURATION.isBookable()).isFalse();
    assertThat(Bookability.NO_BATTERY_READING.isBookable()).isFalse();
  }
}
