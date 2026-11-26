package pt.ua.deti.tqs.p11.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.booking.UsageRules;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * Rules of use for bookings read from configuration (app.booking.*). Until the Client Rules module
 * exists (US14), the client changes them here. The defaults are the values of the acceptance
 * criteria.
 *
 * @param maxDuration BR5: the longest booking
 * @param deposit BR11: the deposit per vehicle type
 * @param startWindow BR7: how long after its planned start a reservation can still be started
 * @param overtimeGrace BR12: overtime not charged
 * @param overtimeBlock BR12: overtime is charged per started block of this length
 * @param overtimePricePerBlock BR12: the price of one block
 */
@ConfigurationProperties("app.booking")
public record BookingProperties(
    @DefaultValue("PT4H") Duration maxDuration,
    @DefaultValue Deposit deposit,
    @DefaultValue("PT15M") Duration startWindow,
    @DefaultValue("PT10M") Duration overtimeGrace,
    @DefaultValue("PT15M") Duration overtimeBlock,
    @DefaultValue("2.00") BigDecimal overtimePricePerBlock) {

  /** Deposit per vehicle type, in euros. */
  public record Deposit(
      @DefaultValue("30.00") BigDecimal eBike, @DefaultValue("50.00") BigDecimal eScooter) {}

  /** Refuses a value the rules do not accept (see UsageRules and BookingRules). */
  public UsageRules toRules() {
    return new UsageRules(
        maxDuration,
        Map.of(
            VehicleType.E_BIKE, new Money(deposit.eBike()),
            VehicleType.E_SCOOTER, new Money(deposit.eScooter())),
        startWindow,
        overtimeGrace,
        overtimeBlock,
        new Money(overtimePricePerBlock));
  }
}
