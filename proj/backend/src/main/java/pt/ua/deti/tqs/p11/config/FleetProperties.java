package pt.ua.deti.tqs.p11.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryRule;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * Fleet rules read from configuration (app.fleet.*). BR1: minBatteryPercent is the floor, and each
 * vehicle type has the percent a started 30 minutes of riding costs. Until the Client Rules module
 * exists (US14), the client changes them here.
 */
@ConfigurationProperties("app.fleet")
public record FleetProperties(
    @DefaultValue("20") int minBatteryPercent,
    @DefaultValue PercentPer30Minutes percentPer30Minutes) {

  /** Percent of battery used in a started 30 minutes, per vehicle type. */
  public record PercentPer30Minutes(
      @DefaultValue("5") int eBike, @DefaultValue("5") int eScooter) {}

  /** Refuses a value the rule does not accept (floor outside 0 to 100, negative rate). */
  public BatteryRule toRule() {
    return new BatteryRule(
        BatteryLevel.of(minBatteryPercent),
        Map.of(
            VehicleType.E_BIKE, percentPer30Minutes.eBike(),
            VehicleType.E_SCOOTER, percentPer30Minutes.eScooter()));
  }
}
