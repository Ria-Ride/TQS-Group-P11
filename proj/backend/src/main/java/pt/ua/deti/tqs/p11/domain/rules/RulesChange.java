package pt.ua.deti.tqs.p11.domain.rules;

import java.time.Duration;
import java.util.Map;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * What the client manager sets in one change of the rules of use (US14): everything except the
 * deposits, which have their own change. It is applied by {@link RulesOfUse#revised}, which checks
 * every value (BR21).
 */
public record RulesChange(
    Duration maxBookingDuration,
    BatteryLevel minBattery,
    Duration startWindow,
    Duration repositioningWindow,
    Duration overtimeGrace,
    Duration overtimeBlock,
    Money overtimeRatePerBlock,
    Map<VehicleType, Integer> batteryPercentPer30Minutes) {}
