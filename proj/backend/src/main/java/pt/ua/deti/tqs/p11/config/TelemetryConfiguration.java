package pt.ua.deti.tqs.p11.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.adapter.in.telemetry.MqttTelemetryConnection;
import pt.ua.deti.tqs.p11.adapter.in.telemetry.MqttTelemetrySubscriber;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.telemetry.TelemetryIngestionPort;
import pt.ua.deti.tqs.p11.application.telemetry.TelemetryIngestionUseCase;

/**
 * Wires telemetry (US09): the core port always, the MQTT adapter only when app.telemetry.enabled is
 * true. A different simulator is a different adapter wired here; the core does not change (K1).
 */
@Configuration
@EnableConfigurationProperties(TelemetryProperties.class)
class TelemetryConfiguration {

  @Bean
  TelemetryIngestionPort telemetryIngestionPort(
      VehicleRepository vehicles, TransactionRunner transactions) {
    return new TelemetryIngestionUseCase(vehicles, transactions);
  }

  @Configuration
  @ConditionalOnProperty(prefix = "app.telemetry", name = "enabled", havingValue = "true")
  static class Mqtt {

    @Bean
    MqttTelemetryConnection mqttTelemetryConnection(
        TelemetryProperties properties, TelemetryIngestionPort ingestion) {
      return new MqttTelemetryConnection(
          properties.host(),
          properties.port(),
          properties.username(),
          properties.password(),
          properties.clientId(),
          properties.fleetId(),
          ingestion);
    }

    @Bean
    MqttTelemetrySubscriber mqttTelemetrySubscriber(MqttTelemetryConnection connection) {
      return connection.subscriber();
    }
  }
}
