package pt.ua.deti.tqs.p11.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pt.ua.deti.tqs.p11.adapter.out.telemetry.LoggingVehicleCommands;
import pt.ua.deti.tqs.p11.adapter.out.telemetry.MqttVehicleCommandPublisher;
import pt.ua.deti.tqs.p11.application.DirectTransactionRunner;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCommandPort;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;

/**
 * Commands to vehicles go over the same MQTT connection as telemetry when it is on; when it is off
 * (the default, and every test without a broker), they are only logged, so a booking still works.
 */
class TelemetryConfigurationTest {

  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(TelemetryConfiguration.class)
          .withBean(VehicleRepository.class, () -> mock(VehicleRepository.class))
          .withBean(TransactionRunner.class, DirectTransactionRunner::new)
          .withBean(Clock.class, Clock::systemUTC);

  @Test
  void telemetryOff_commandsAreOnlyLogged() {
    context.run(started ->
        assertThat(started)
            .getBean(VehicleCommandPort.class)
            .isInstanceOf(LoggingVehicleCommands.class));
  }

  @Test
  void telemetryOn_commandsArePublishedOverMqtt() {
    context
        .withPropertyValues("app.telemetry.enabled=true")
        .run(started ->
            assertThat(started)
                .getBean(VehicleCommandPort.class)
                .isInstanceOf(MqttVehicleCommandPublisher.class));
  }
}
