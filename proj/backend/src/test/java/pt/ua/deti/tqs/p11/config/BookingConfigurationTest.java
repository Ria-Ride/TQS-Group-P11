package pt.ua.deti.tqs.p11.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pt.ua.deti.tqs.p11.application.booking.UsageRulesPort;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * The rules-of-use port as the application gets it: from app.booking.* until US14 replaces the
 * adapter, and checked at start-up, so an invalid value never reaches a booking.
 */
class BookingConfigurationTest {

  private final ApplicationContextRunner context =
      new ApplicationContextRunner().withUserConfiguration(BookingConfiguration.class);

  @Test
  void usageRules_comeFromTheProperties() {
    context
        .withPropertyValues("app.booking.max-duration=PT3H", "app.booking.deposit.e-bike=20.00")
        .run(started -> {
          UsageRulesPort port = started.getBean(UsageRulesPort.class);

          assertThat(port.current().maxBookingDuration()).isEqualTo(Duration.ofHours(3));
          assertThat(port.current().forType(VehicleType.E_BIKE).deposit())
              .isEqualTo(Money.of("20.00"));
        });
  }

  @Test
  void invalidRules_stopTheApplicationFromStarting() {
    context
        .withPropertyValues("app.booking.overtime-block=PT0S")
        .run(started -> assertThat(started).hasFailed());
  }
}
