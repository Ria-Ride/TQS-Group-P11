package pt.ua.deti.tqs.p11.adapter.out.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.support.MosquittoBroker;

/**
 * The unlock command through a real broker, received by a client that subscribes as the simulator
 * does (fleet/{fleetId}/vehicles/+/commands/+, QoS 1).
 */
class MqttVehicleCommandPublisherIT {

  private static final MosquittoBroker BROKER = new MosquittoBroker();
  private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

  private static Mqtt5AsyncClient platform;
  private static Mqtt5BlockingClient simulator;
  private static Mqtt5BlockingClient.Mqtt5Publishes commands;

  @BeforeAll
  static void connect() {
    BROKER.start();
    platform =
        Mqtt5Client.builder()
            .identifier("platform-it-" + UUID.randomUUID())
            .serverHost(BROKER.host())
            .serverPort(BROKER.port())
            .buildAsync();
    platform.connect().join();
    simulator =
        Mqtt5Client.builder()
            .identifier("simulator-it-" + UUID.randomUUID())
            .serverHost(BROKER.host())
            .serverPort(BROKER.port())
            .buildBlocking();
    simulator.connect();
    commands = simulator.publishes(MqttGlobalPublishFilter.ALL);
    simulator
        .subscribeWith()
        .topicFilter("fleet/fleet-01/vehicles/+/commands/+")
        .qos(MqttQos.AT_LEAST_ONCE)
        .send();
  }

  @AfterAll
  static void disconnect() {
    commands.close();
    simulator.disconnect();
    platform.disconnect().join();
    BROKER.stop();
  }

  private static MqttVehicleCommandPublisher publisher(Mqtt5AsyncClient client) {
    Iterator<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID()).iterator();
    return new MqttVehicleCommandPublisher(
        client, "fleet-01", Duration.ofSeconds(30), Clock.fixed(NOW, ZoneOffset.UTC), ids::next);
  }

  @Test
  void unlock_isReceivedOnTheVehiclesUnlockTopic() throws InterruptedException {
    publisher(platform).unlock(FleetCode.of("bike-0001"));

    Mqtt5Publish received =
        commands
            .receive(5, TimeUnit.SECONDS)
            .orElseThrow(() -> new AssertionError("No command reached the simulator"));
    assertThat(received.getTopic())
        .hasToString("fleet/fleet-01/vehicles/bike-0001/commands/unlock");
    assertThat(received.getQos()).isEqualTo(MqttQos.AT_LEAST_ONCE);
    assertThat(received.isRetain()).isFalse();
    assertThat(new String(received.getPayloadAsBytes(), StandardCharsets.UTF_8))
        .contains("\"type\":\"command\"")
        .contains("\"vehicleId\":\"bike-0001\"")
        .contains("\"timestamp\":\"2026-11-26T10:00:00Z\"")
        .contains("\"expiresAt\":\"2026-11-26T10:00:30Z\"");
  }

  /** Decision 7: the booking is already made; a broker that is not there is only logged. */
  @Test
  void unlock_withoutAConnection_doesNotThrow() {
    Mqtt5AsyncClient neverConnected =
        Mqtt5Client.builder().serverHost(BROKER.host()).serverPort(BROKER.port()).buildAsync();

    assertThatCode(() -> publisher(neverConnected).unlock(FleetCode.of("bike-0001")))
        .doesNotThrowAnyException();
  }
}
