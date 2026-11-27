package pt.ua.deti.tqs.p11.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** The SLA of maintenance tickets (BR19) as it is read from app.maintenance.*. */
class MaintenancePropertiesTest {

  private static MaintenanceProperties bind(Map<String, String> properties) {
    return new Binder(new MapConfigurationPropertySource(properties))
        .bindOrCreate("app.maintenance", MaintenanceProperties.class);
  }

  @Test
  void default_isA24HourSla() {
    assertThat(bind(Map.of()).sla()).isEqualTo(Duration.ofHours(24));
  }

  @Test
  void slaHours_isRead() {
    assertThat(bind(Map.of("app.maintenance.sla-hours", "8")).sla()).isEqualTo(Duration.ofHours(8));
  }

  /** A ticket could never be on time with no repair time, so the application must not start. */
  @ParameterizedTest
  @ValueSource(strings = {"0", "-1"})
  void slaHoursNotPositive_isRefused(String hours) {
    Map<String, String> properties = Map.of("app.maintenance.sla-hours", hours);

    assertThatThrownBy(() -> bind(properties)).isInstanceOf(BindException.class);
  }
}
