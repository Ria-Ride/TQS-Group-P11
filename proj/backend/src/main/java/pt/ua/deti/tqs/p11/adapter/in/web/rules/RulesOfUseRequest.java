package pt.ua.deti.tqs.p11.adapter.in.web.rules;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import pt.ua.deti.tqs.p11.adapter.in.web.InvalidFieldException;
import pt.ua.deti.tqs.p11.adapter.in.web.validation.MoneyFields;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.rules.RulesChange;

/**
 * Body of PUT /usage-rules (US14, BR21): every value of the rules of use except the deposits, in
 * whole minutes and percent. The limits are those of the domain, so the API and the domain cannot
 * disagree; the account that makes the change is the caller's, never the body.
 */
record RulesOfUseRequest(
    @Schema(description = "The longest booking, in minutes: more than 0 and at most 24 hours")
        @NotNull
        @Min(1)
        @Max(1440)
        Integer maxBookingMinutes,
    @Schema(description = "The minimum battery to book, in percent: from 0 to 100")
        @NotNull
        @Min(0)
        @Max(100)
        Integer minBatteryPercent,
    @Schema(description = "How long a reservation can wait to be started, in minutes")
        @NotNull
        @Min(1)
        @Max(1440)
        Integer startWindowMinutes,
    @Schema(description = "How long a user has to reposition after a refused return, in minutes")
        @NotNull
        @Min(1)
        @Max(1440)
        Integer repositioningWindowMinutes,
    @Schema(description = "Minutes of overtime that are not charged: from 0 to 24 hours")
        @NotNull
        @Min(0)
        @Max(1440)
        Integer overtimeGraceMinutes,
    @Schema(description = "Length of a block of overtime, in minutes") @NotNull @Min(1) @Max(1440)
        Integer overtimeBlockMinutes,
    @Schema(
            description = "Price of one block of overtime, with at most two decimals",
            example = "1.50")
        @NotNull
        @Pattern(regexp = MoneyFields.AMOUNT, message = MoneyFields.MESSAGE)
        String overtimeRatePerBlock,
    @Schema(
            description =
                "Percent of battery that every started 30 minutes of riding uses, for each"
                    + " vehicle type (BR1)",
            example = "{\"E_BIKE\": 5, \"E_SCOOTER\": 5}")
        @NotNull
        Map<VehicleType, Integer> batteryPercentPer30Minutes) {

  private static final int MAX_PERCENT = 100;

  RulesChange toChange() {
    Map<VehicleType, Integer> rates = new EnumMap<>(VehicleType.class);
    for (VehicleType type : VehicleType.values()) {
      Integer rate = batteryPercentPer30Minutes.get(type);
      if (rate == null || rate < 0 || rate > MAX_PERCENT) {
        throw new InvalidFieldException(
            "batteryPercentPer30Minutes",
            "must give 0 to 100 for each vehicle type, missing or wrong: " + type);
      }
      rates.put(type, rate);
    }
    return new RulesChange(
        Duration.ofMinutes(maxBookingMinutes),
        BatteryLevel.of(minBatteryPercent),
        Duration.ofMinutes(startWindowMinutes),
        Duration.ofMinutes(repositioningWindowMinutes),
        Duration.ofMinutes(overtimeGraceMinutes),
        Duration.ofMinutes(overtimeBlockMinutes),
        Money.of(overtimeRatePerBlock),
        rates);
  }
}
