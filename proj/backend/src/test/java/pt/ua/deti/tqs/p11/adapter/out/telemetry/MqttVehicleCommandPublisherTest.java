package pt.ua.deti.tqs.p11.adapter.out.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;

/**
 * The unlock command as the simulator contract 1.0.0 defines it: the topic and the message. The
 * values are those of contract/examples/valid/command-unlock.json, so the expected message is that
 * example, field for field.
 */
class MqttVehicleCommandPublisherTest {

  private static final UUID MESSAGE_ID = UUID.fromString("c3a1f8d0-6a24-4f67-b1d2-9e3e4a5b6c03");
  private static final UUID COMMAND_ID = UUID.fromString("e5b2a9c1-0d3f-4a8e-8b77-1c2d3e4f5a04");
  private static final Instant SENT = Instant.parse("2026-11-10T10:10:00Z");

  /** Never connected: building the publisher needs a client, not a broker. */
  private static final Mqtt5AsyncClient OFFLINE = Mqtt5Client.builder().buildAsync();

  @Test
  void unlockTopic_isTheVehiclesUnlockCommandTopic() {
    assertThat(MqttVehicleCommandPublisher.unlockTopic("fleet-01", FleetCode.of("bike-0042")))
        .isEqualTo("fleet/fleet-01/vehicles/bike-0042/commands/unlock");
  }

  @Test
  void unlockMessage_isTheCommandOfTheContract() {
    String message =
        MqttVehicleCommandPublisher.unlockMessage(
            "fleet-01",
            FleetCode.of("bike-0042"),
            MESSAGE_ID,
            COMMAND_ID,
            SENT,
            SENT.plusSeconds(30));

    assertThat(message)
        .isEqualTo(
            "{\"schemaVersion\":\"1.0.0\","
                + "\"messageId\":\"c3a1f8d0-6a24-4f67-b1d2-9e3e4a5b6c03\","
                + "\"type\":\"command\","
                + "\"timestamp\":\"2026-11-10T10:10:00Z\","
                + "\"fleetId\":\"fleet-01\","
                + "\"vehicleId\":\"bike-0042\","
                + "\"data\":{\"commandId\":\"e5b2a9c1-0d3f-4a8e-8b77-1c2d3e4f5a04\","
                + "\"expiresAt\":\"2026-11-10T10:10:30Z\"}}");
  }

  /** The fleet id goes into topics and messages, so it follows the contract's identifiers. */
  @Test
  void new_fleetIdThatIsNotAnIdentifier_isRefused() {
    Clock clock = Clock.systemUTC();
    Duration ttl = Duration.ofSeconds(30);

    assertThatThrownBy(
            () -> new MqttVehicleCommandPublisher(OFFLINE, "fleet/01", ttl, clock, UUID::randomUUID))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** A command that expires at once, or before it is sent, would always be rejected. */
  @Test
  void new_ttlThatIsNotPositive_isRefused() {
    Clock clock = Clock.systemUTC();

    assertThatThrownBy(
            () ->
                new MqttVehicleCommandPublisher(
                    OFFLINE, "fleet-01", Duration.ZERO, clock, UUID::randomUUID))
        .isInstanceOf(IllegalArgumentException.class);
  }
}