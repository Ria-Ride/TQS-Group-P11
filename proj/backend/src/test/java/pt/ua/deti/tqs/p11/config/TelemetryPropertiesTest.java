package pt.ua.deti.tqs.p11.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * The MQTT settings of the telemetry adapter (app.telemetry.*): off by default, simulator's names,
 * and how long a command to a vehicle stays valid.
 */
class TelemetryPropertiesTest {

  private static TelemetryProperties bind(Map<String, String> properties) {
    return new Binder(new MapConfigurationPropertySource(properties))
        .bind("app.telemetry", TelemetryProperties.class)
        .orElseGet(() ->
            new TelemetryProperties(
                false,
                "localhost",
                1883,
                "",
                "",
                "",
                "fleet-01",
                Duration.ofSeconds(30),
                Duration.ofSeconds(10)));
  }

  @Test
  void defaults_areOffAndALocalBrokerOnTheMqttPortForTheSimulatorsDefaultFleet() {
    TelemetryProperties defaults = bind(Map.of("app.telemetry.host", "localhost"));

    assertThat(defaults.enabled()).isFalse();
    assertThat(defaults.host()).isEqualTo("localhost");
    assertThat(defaults.port()).isEqualTo(1883);
    assertThat(defaults.username()).isEmpty();
    assertThat(defaults.password()).isEmpty();
    assertThat(defaults.clientId()).isEmpty();
    assertThat(defaults.fleetId()).isEqualTo("fleet-01");
    assertThat(defaults.commandTtl()).isEqualTo(Duration.ofSeconds(30));
    assertThat(defaults.commandResponseTimeout()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  void settings_areReadFromTheirNames() {
    TelemetryProperties properties =
        bind(
            Map.of(
                "app.telemetry.enabled", "true",
                "app.telemetry.host", "broker",
                "app.telemetry.port", "1884",
                "app.telemetry.username", "platform",
                "app.telemetry.password", "secret",
                "app.telemetry.client-id", "platform-1",
                "app.telemetry.fleet-id", "fleet-02",
                "app.telemetry.command-ttl", "PT10S",
                "app.telemetry.command-response-timeout", "PT3S"));

    assertThat(properties)
        .isEqualTo(
            new TelemetryProperties(
                true,
                "broker",
                1884,
                "platform",
                "secret",
                "platform-1",
                "fleet-02",
                Duration.ofSeconds(10),
                Duration.ofSeconds(3)));
  }
}
