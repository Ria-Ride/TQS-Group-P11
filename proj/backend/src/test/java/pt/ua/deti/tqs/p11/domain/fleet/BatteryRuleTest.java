package pt.ua.deti.tqs.p11.domain.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * BR1 with a duration (US02): the battery a ride needs is the floor plus a rate for every started
 * 30 minutes, and the rate depends on the vehicle type.
 */
class BatteryRuleTest {

  private static final BatteryLevel FLOOR_20 = BatteryLevel.of(20);

  private static BatteryRule rule(int bikePercent, int scooterPercent) {
    return new BatteryRule(
        FLOOR_20, Map.of(VehicleType.E_BIKE, bikePercent, VehicleType.E_SCOOTER, scooterPercent));
  }

  @Test
  void requiredPercent_noDuration_isTheFloor() {
    assertThat(rule(5, 5).floorPercent()).isEqualTo(20);
  }

  /** Every started block of 30 minutes counts: 30 minutes is one block, 31 minutes is two. */
  @ParameterizedTest(name = "{0} minutes need {1}%")
  @CsvSource({
    "1, 25",
    "29, 25",
    "30, 25",
    "31, 30",
    "60, 30",
    "61, 35",
    "90, 35",
    "120, 40",
    "1440, 260"
  })
  void requiredPercent_duration_isTheFloorPlusFivePercentPerStartedHalfHour(
      int minutes, int expected) {
    assertThat(rule(5, 5).requiredPercent(VehicleType.E_BIKE, minutes)).isEqualTo(expected);
  }

  @Test
  void requiredPercent_usesTheRateOfTheVehicleType() {
    BatteryRule rule = rule(5, 8);

    assertThat(rule.requiredPercent(VehicleType.E_BIKE, 60)).isEqualTo(30);
    assertThat(rule.requiredPercent(VehicleType.E_SCOOTER, 60)).isEqualTo(36);
  }

  @Test
  void requiredPercent_isNotCappedAt100_soAnImpossibleRideNeedsMoreThanAFullBattery() {
    assertThat(rule(5, 5).requiredPercent(VehicleType.E_BIKE, 1440)).isGreaterThan(100);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 1441})
  void requiredPercent_durationOutsideOneToADay_isRefused(int minutes) {
    assertThatThrownBy(() -> rule(5, 5).requiredPercent(VehicleType.E_BIKE, minutes))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void checkMinutes_acceptsTheLimits() {
    BatteryRule.checkMinutes(1);
    BatteryRule.checkMinutes(BatteryRule.MAX_MINUTES);
  }

  @Test
  void constructor_rateMissingForAType_isRefused() {
    Map<VehicleType, Integer> onlyBikes = Map.of(VehicleType.E_BIKE, 5);

    assertThatThrownBy(() -> new BatteryRule(FLOOR_20, onlyBikes))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_negativeRate_isRefused() {
    assertThatThrownBy(() -> rule(-1, 5)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_noFloor_isRefused() {
    assertThatThrownBy(
            () -> new BatteryRule(null, Map.of(VehicleType.E_BIKE, 5, VehicleType.E_SCOOTER, 5)))
        .isInstanceOf(NullPointerException.class);
  }
}
