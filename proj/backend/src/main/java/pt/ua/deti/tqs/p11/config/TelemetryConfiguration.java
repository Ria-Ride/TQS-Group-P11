package pt.ua.deti.tqs.p11.config;

import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.adapter.in.telemetry.MqttTelemetryConnection;
import pt.ua.deti.tqs.p11.adapter.in.telemetry.MqttTelemetrySubscriber;
import pt.ua.deti.tqs.p11.adapter.out.telemetry.LoggingVehicleCommands;
import pt.ua.deti.tqs.p11.adapter.out.telemetry.MqttVehicleCommandPublisher;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCommandPort;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.telemetry.TelemetryIngestionPort;
import pt.ua.deti.tqs.p11.application.telemetry.TelemetryIngestionUseCase;

/**
 * Wires telemetry (US09) and commands to vehicles (US04): the core port always, the MQTT adapters
 * only when app.telemetry.enabled is true; otherwise commands are only logged. A different
 * simulator is a different adapter wired here; the core does not change (K1).
 */
@Configuration
@EnableConfigurationProperties(TelemetryProperties.class)
class TelemetryConfiguration {

  @Bean
  TelemetryIngestionPort telemetryIngestionPort(
      VehicleRepository vehicles, TransactionRunner transactions) {
    return new TelemetryIngestionUseCase(vehicles, transactions);
  }

  /** Telemetry off: commands to vehicles are only logged (US04). */
  @Bean
  @ConditionalOnProperty(
      prefix = "app.telemetry",
      name = "enabled",
      havingValue = "false",
      matchIfMissing = true)
  VehicleCommandPort loggingVehicleCommands() {
    return new LoggingVehicleCommands();
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

    /** Commands to vehicles over the telemetry connection (US04, simulator contract). */
    @Bean
    VehicleCommandPort mqttVehicleCommands(
        MqttTelemetryConnection connection, TelemetryProperties properties, Clock clock) {
      return new MqttVehicleCommandPublisher(
          connection.client(),
          properties.fleetId(),
          properties.commandTtl(),
          clock,
          UUID::randomUUID);
    }
  }
}
