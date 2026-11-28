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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.application.fleet.LockResult;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.support.MosquittoBroker;
import tools.jackson.databind.json.JsonMapper;

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

  // ------------------------------------------------------------- US06: lock, with the answer

  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** A publisher that listens for answers, as the telemetry connection makes it do. */
  private static MqttVehicleCommandPublisher listening(Duration timeout) {
    MqttVehicleCommandPublisher publisher =
        new MqttVehicleCommandPublisher(
            platform,
            "fleet-01",
            Duration.ofSeconds(30),
            timeout,
            Clock.systemUTC(),
            UUID::randomUUID);
    publisher.listenForResponses().join();
    return publisher;
  }

  /**
   * As the simulator: waits for the next lock command and answers it on the vehicle's responses
   * topic, with the command's id (or another one, to check that it is ignored).
   */
  private static CompletableFuture<Void> answerTheNextLock(
      String status, String reason, boolean sameCommand) {
    return CompletableFuture.runAsync(() -> {
      try {
        Mqtt5Publish command =
            commands
                .receive(5, TimeUnit.SECONDS)
                .orElseThrow(() -> new AssertionError("No lock reached the simulator"));
        String topic = command.getTopic().toString();
        assertThat(topic).endsWith("/commands/lock");
        String vehicle = topic.split("/")[3];
        String commandId =
            sameCommand
                ? JSON.readTree(command.getPayloadAsBytes()).get("data").get("commandId").asString()
                : UUID.randomUUID().toString();
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        String answer =
            ("{\"schemaVersion\":\"1.0.0\",\"messageId\":\"%s\",\"type\":\"response\","
                    + "\"timestamp\":\"2026-11-26T10:00:01Z\",\"fleetId\":\"fleet-01\","
                    + "\"vehicleId\":\"%s\",\"data\":{\"commandId\":\"%s\",\"command\":\"lock\","
                    + "\"status\":\"%s\",\"reason\":%s}}")
                .formatted(UUID.randomUUID(), vehicle, commandId, status, reasonJson);
        simulator
            .publishWith()
            .topic("fleet/fleet-01/vehicles/" + vehicle + "/responses")
            .qos(MqttQos.AT_LEAST_ONCE)
            .payload(answer.getBytes(StandardCharsets.UTF_8))
            .send();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    });
  }

  @Test
  void lock_answeredCompleted_isLocked() {
    MqttVehicleCommandPublisher publisher = listening(Duration.ofSeconds(5));
    CompletableFuture<Void> simulated = answerTheNextLock("COMPLETED", null, true);

    LockResult result = publisher.lock(FleetCode.of("bike-0002"));

    simulated.join();
    assertThat(result).isEqualTo(new LockResult.Locked());
  }

  @Test
  void lock_answeredRejected_isRefusedWithTheReason() {
    MqttVehicleCommandPublisher publisher = listening(Duration.ofSeconds(5));
    CompletableFuture<Void> simulated = answerTheNextLock("REJECTED", "NOT_AT_HUB", true);

    LockResult result = publisher.lock(FleetCode.of("bike-0003"));

    simulated.join();
    assertThat(result).isEqualTo(new LockResult.Refused("NOT_AT_HUB"));
  }

  /** An answer to some other command does not count; with none for ours, it times out. */
  @Test
  void lock_answerToAnotherCommandOnly_isNoAnswerAfterTheTimeout() {
    MqttVehicleCommandPublisher publisher = listening(Duration.ofSeconds(1));
    CompletableFuture<Void> simulated = answerTheNextLock("COMPLETED", null, false);

    LockResult result = publisher.lock(FleetCode.of("bike-0004"));

    simulated.join();
    assertThat(result).isEqualTo(new LockResult.NoAnswer());
  }

  @Test
  void lock_withoutAConnection_isNoAnswer() {
    Mqtt5AsyncClient neverConnected =
        Mqtt5Client.builder().serverHost(BROKER.host()).serverPort(BROKER.port()).buildAsync();
    MqttVehicleCommandPublisher publisher =
        new MqttVehicleCommandPublisher(
            neverConnected,
            "fleet-01",
            Duration.ofSeconds(30),
            Duration.ofSeconds(1),
            Clock.systemUTC(),
            UUID::randomUUID);

    assertThat(publisher.lock(FleetCode.of("bike-0005"))).isEqualTo(new LockResult.NoAnswer());
  }
}
