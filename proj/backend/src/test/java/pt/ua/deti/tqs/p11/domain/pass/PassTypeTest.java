package pt.ua.deti.tqs.p11.domain.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/** BR8 (validity of a pass) and the price of a pass with and without a stay code (BR9). */
class PassTypeTest {

  private static final Instant PAID_AT = Instant.parse("2026-11-24T10:15:30Z");

  private static PassType type(int validityDays, String basePrice) {
    return new PassType(
        UUID.randomUUID(),
        "DAY",
        "Day pass",
        validityDays,
        Money.of(basePrice),
        Set.of(VehicleType.E_BIKE, VehicleType.E_SCOOTER));
  }

  private static Stay stayWithDiscount(int percent) {
    return new Stay(
        UUID.randomUUID(),
        "RIA-1001",
        new PartnerAccommodation(UUID.randomUUID(), "Hotel Ria Azul", percent),
        LocalDate.parse("2026-11-23"),
        LocalDate.parse("2026-11-25"),
        null);
  }

  /** BR8: DAY lasts 1 day and MULTI_DAY 3 days, each day exactly 24 hours. */
  @ParameterizedTest(name = "{0} day(s) last {1} hours")
  @CsvSource({"1, 24", "3, 72"})
  void validUntil_isValidityDaysTimes24HoursAfterPayment(int days, int hours) {
    Instant validTo = type(days, "10.00").validUntil(PAID_AT);

    assertThat(Duration.between(PAID_AT, validTo)).isEqualTo(Duration.ofHours(hours));
  }

  /**
   * Lisbon changes from summer to winter time on 2026-10-25: that calendar day has 25 hours. A day
   * pass still lasts 24 hours, because validity counts hours, not calendar days.
   */
  @Test
  void validUntil_overADaylightSavingChange_isStill24Hours() {
    Instant beforeChange = Instant.parse("2026-10-24T22:00:00Z");

    assertThat(type(1, "10.00").validUntil(beforeChange))
        .isEqualTo(Instant.parse("2026-10-25T22:00:00Z"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void new_validityBelowOneDay_isRefused(int days) {
    assertThatThrownBy(() -> type(days, "10.00")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_oneDay_isAccepted() {
    assertThat(type(1, "10.00").validityDays()).isEqualTo(1);
  }

  @Test
  void new_noVehicleType_isRefused() {
    assertThatThrownBy(
            () -> new PassType(UUID.randomUUID(), "DAY", "Day pass", 1, Money.of("10"), Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_blankCode_isRefused() {
    assertThatThrownBy(() ->
            new PassType(
                UUID.randomUUID(), " ", "Day pass", 1, Money.of("10"), Set.of(VehicleType.E_BIKE)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** BR9 examples: 10.00 becomes 9.00 and 25.00 becomes 22.50 at 10% off. */
  @ParameterizedTest(name = "{0} with 10% off is {1}")
  @CsvSource({"10.00, 9.00", "25.00, 22.50"})
  void priceWith_aStayCode_appliesTheHotelDiscount(String basePrice, String expected) {
    assertThat(type(1, basePrice).priceWith(stayWithDiscount(10))).isEqualTo(Money.of(expected));
  }
}
