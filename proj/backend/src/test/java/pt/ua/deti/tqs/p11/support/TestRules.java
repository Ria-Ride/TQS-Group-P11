package pt.ua.deti.tqs.p11.support;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.rules.RulesOfUse;
import pt.ua.deti.tqs.p11.domain.rules.VehicleTypeRules;

/**
 * The rules of use the booking tests are written with, so they do not depend on the seeded version
 * nor on what the client manager changed (US14): the longest booking is 4 hours; a ride needs the
 * 20% floor plus 5% per started 30 minutes (BR1); the deposit is 30.00 EUR for an E_BIKE and 50.00
 * EUR for an E_SCOOTER (BR11); a reservation can start up to 15 minutes late (BR7); overtime is
 * charged after 10 minutes of grace, at 2.00 EUR per started block of 15 minutes (BR12).
 */
public final class TestRules {

  public static final Instant CREATED_AT = Instant.parse("2026-11-26T00:00:00Z");

  private TestRules() {}

  /** As version 1. */
  public static RulesOfUse bookingRules() {
    return bookingRules(1, CREATED_AT);
  }

  /** As the given version, to be saved after the ones already stored. */
  public static RulesOfUse bookingRules(int version, Instant createdAt) {
    return new RulesOfUse(
        version,
        createdAt,
        null,
        Duration.ofHours(4),
        BatteryLevel.of(20),
        Duration.ofMinutes(15),
        Duration.ofMinutes(10),
        Duration.ofMinutes(10),
        Duration.ofMinutes(15),
        Money.of("2.00"),
        Map.of(
            VehicleType.E_BIKE, new VehicleTypeRules(Money.of("30.00"), 5),
            VehicleType.E_SCOOTER, new VehicleTypeRules(Money.of("50.00"), 5)));
  }
}