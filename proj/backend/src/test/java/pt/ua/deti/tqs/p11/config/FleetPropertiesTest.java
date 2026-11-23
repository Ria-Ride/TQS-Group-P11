package pt.ua.deti.tqs.p11.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryRule;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/** The battery rule (BR1) as it is read from app.fleet.* in application.properties. */
class FleetPropertiesTest {

  private static FleetProperties bind(Map<String, String> properties) {
    return new Binder(new MapConfigurationPropertySource(properties))
        .bind("app.fleet", FleetProperties.class)
        .orElseGet(() -> new FleetProperties(20, new FleetProperties.PercentPer30Minutes(5, 5)));
  }

  @Test
  void defaults_areA20PercentFloorAndFivePercentPerHalfHourForEveryType() {
    BatteryRule rule = bind(Map.of("app.fleet.min-battery-percent", "20")).toRule();

    assertThat(rule.floorPercent()).isEqualTo(20);
    assertThat(rule.requiredPercent(VehicleType.E_BIKE, 30)).isEqualTo(25);
    assertThat(rule.requiredPercent(VehicleType.E_SCOOTER, 30)).isEqualTo(25);
  }

  @Test
  void rates_areReadPerVehicleType() {
    FleetProperties properties =
        bind(
            Map.of(
                "app.fleet.min-battery-percent", "25",
                "app.fleet.percent-per-30-minutes.e-bike", "4",
                "app.fleet.percent-per-30-minutes.e-scooter", "9"));

    BatteryRule rule = properties.toRule();

    assertThat(rule.floorPercent()).isEqualTo(25);
    assertThat(rule.requiredPercent(VehicleType.E_BIKE, 30)).isEqualTo(29);
    assertThat(rule.requiredPercent(VehicleType.E_SCOOTER, 30)).isEqualTo(34);
  }

  @Test
  void floorOutsideZeroToHundred_isRefusedWhenTheRuleIsBuilt() {
    FleetProperties properties = bind(Map.of("app.fleet.min-battery-percent", "101"));

    assertThatThrownBy(properties::toRule).isInstanceOf(IllegalArgumentException.class);
  }
}
