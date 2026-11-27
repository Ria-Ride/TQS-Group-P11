package pt.ua.deti.tqs.p11.adapter.in.web.rules;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.rules.RulesOfUse;

/**
 * A version of the rules of use. createdBy is the account that made it, null for the first version.
 * Durations are in minutes and amounts are strings.
 */
record RulesOfUseResponse(
    int version,
    Instant createdAt,
    UUID createdBy,
    int maxBookingMinutes,
    int minBatteryPercent,
    int startWindowMinutes,
    int repositioningWindowMinutes,
    int overtimeGraceMinutes,
    int overtimeBlockMinutes,
    String overtimeRatePerBlock,
    String currency,
    List<VehicleTypeResponse> vehicleTypes) {

  static RulesOfUseResponse from(RulesOfUse rules) {
    return new RulesOfUseResponse(
        rules.version(),
        rules.createdAt(),
        rules.createdBy(),
        (int) rules.maxBookingDuration().toMinutes(),
        rules.minBattery().percent(),
        (int) rules.startWindow().toMinutes(),
        (int) rules.repositioningWindow().toMinutes(),
        (int) rules.overtimeGrace().toMinutes(),
        (int) rules.overtimeBlock().toMinutes(),
        rules.overtimeRatePerBlock().toString(),
        Money.CURRENCY,
        Arrays.stream(VehicleType.values())
            .map(type -> VehicleTypeResponse.of(rules, type))
            .toList());
  }
}
