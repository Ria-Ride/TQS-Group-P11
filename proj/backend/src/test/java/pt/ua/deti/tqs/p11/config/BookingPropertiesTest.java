package pt.ua.deti.tqs.p11.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.booking.UsageRules;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/** The rules of use for bookings as they are read from app.booking.* (seeded until US14). */
class BookingPropertiesTest {

  private static BookingProperties bind(Map<String, String> properties) {
    return new Binder(new MapConfigurationPropertySource(properties))
        .bindOrCreate("app.booking", BookingProperties.class);
  }

  /** The values of the acceptance criteria of US04 and US06. */
  @Test
  void defaults_areTheSeededRulesOfUse() {
    UsageRules rules = bind(Map.of()).toRules();

    assertThat(rules.maxBookingDuration()).isEqualTo(Duration.ofHours(4));
    assertThat(rules.forType(VehicleType.E_BIKE).deposit()).isEqualTo(Money.of("30.00"));
    assertThat(rules.forType(VehicleType.E_SCOOTER).deposit()).isEqualTo(Money.of("50.00"));
    assertThat(rules.startWindow()).isEqualTo(Duration.ofMinutes(15));
    assertThat(rules.overtimeGrace()).isEqualTo(Duration.ofMinutes(10));
    assertThat(rules.overtimeBlock()).isEqualTo(Duration.ofMinutes(15));
    assertThat(rules.overtimePricePerBlock()).isEqualTo(Money.of("2.00"));
  }

  @Test
  void everyValue_isReadFromTheProperties() {
    UsageRules rules =
        bind(Map.of(
                "app.booking.max-duration", "PT2H",
                "app.booking.deposit.e-bike", "25.00",
                "app.booking.deposit.e-scooter", "45.50",
                "app.booking.start-window", "PT20M",
                "app.booking.overtime-grace", "PT5M",
                "app.booking.overtime-block", "PT30M",
                "app.booking.overtime-price-per-block", "3.50"))
            .toRules();

    assertThat(rules.maxBookingDuration()).isEqualTo(Duration.ofHours(2));
    assertThat(rules.forType(VehicleType.E_BIKE).deposit()).isEqualTo(Money.of("25.00"));
    assertThat(rules.forType(VehicleType.E_SCOOTER).deposit()).isEqualTo(Money.of("45.50"));
    assertThat(rules.startWindow()).isEqualTo(Duration.ofMinutes(20));
    assertThat(rules.overtimeGrace()).isEqualTo(Duration.ofMinutes(5));
    assertThat(rules.overtimeBlock()).isEqualTo(Duration.ofMinutes(30));
    assertThat(rules.overtimePricePerBlock()).isEqualTo(Money.of("3.50"));
  }

  @Test
  void maxDurationAboveOneDay_isRefusedWhenTheRulesAreBuilt() {
    BookingProperties properties = bind(Map.of("app.booking.max-duration", "PT25H"));

    assertThatThrownBy(properties::toRules).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void depositWithMoreThanTwoDecimals_isRefusedWhenTheRulesAreBuilt() {
    BookingProperties properties = bind(Map.of("app.booking.deposit.e-bike", "30.001"));

    assertThatThrownBy(properties::toRules).isInstanceOf(IllegalArgumentException.class);
  }
}
