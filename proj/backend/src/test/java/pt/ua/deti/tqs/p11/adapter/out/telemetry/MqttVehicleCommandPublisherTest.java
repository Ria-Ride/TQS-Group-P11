package pt.ua.deti.tqs.p11.adapter.out.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.application.fleet.LockResult;
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

    assertThatThrownBy(() ->
            new MqttVehicleCommandPublisher(OFFLINE, "fleet/01", ttl, clock, UUID::randomUUID))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** A command that expires at once, or before it is sent, would always be rejected. */
  @Test
  void new_ttlThatIsNotPositive_isRefused() {
    Clock clock = Clock.systemUTC();

    assertThatThrownBy(() ->
            new MqttVehicleCommandPublisher(
                OFFLINE, "fleet-01", Duration.ZERO, clock, UUID::randomUUID))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ------------------------------------------------------------- US06: lock and its answer

  private static final String RESPONSES = "fleet/fleet-01/vehicles/bike-0042/responses";

  private static byte[] response(String vehicle, String command, String status, String reason) {
    String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
    return ("{\"schemaVersion\":\"1.0.0\",\"messageId\":\"1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c05\","
            + "\"type\":\"response\",\"timestamp\":\"2026-11-10T10:10:01Z\","
            + "\"fleetId\":\"fleet-01\",\"vehicleId\":\""
            + vehicle
            + "\","
            + "\"data\":{\"commandId\":\""
            + COMMAND_ID
            + "\",\"command\":\""
            + command
            + "\",\"status\":\""
            + status
            + "\",\"reason\":"
            + reasonJson
            + "}}")
        .getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void lockTopic_isTheVehiclesLockCommandTopic() {
    assertThat(
            MqttVehicleCommandPublisher.commandTopic("fleet-01", FleetCode.of("bike-0042"), "lock"))
        .isEqualTo("fleet/fleet-01/vehicles/bike-0042/commands/lock");
  }

  @Test
  void answerOf_completedLock_isLocked() {
    assertThat(
            MqttVehicleCommandPublisher.answerOf(
                "fleet-01", RESPONSES, response("bike-0042", "lock", "COMPLETED", null)))
        .contains(new MqttVehicleCommandPublisher.Answer(COMMAND_ID, new LockResult.Locked()));
  }

  @Test
  void answerOf_rejectedLock_isRefusedWithTheReason() {
    assertThat(
            MqttVehicleCommandPublisher.answerOf(
                "fleet-01", RESPONSES, response("bike-0042", "lock", "REJECTED", "NOT_AT_HUB")))
        .map(MqttVehicleCommandPublisher.Answer::result)
        .contains(new LockResult.Refused("NOT_AT_HUB"));
  }

  /** FAILED has a reason in the contract; without one, the status stands in for it. */
  @Test
  void answerOf_failedLockWithoutAReason_isRefusedWithTheStatus() {
    assertThat(
            MqttVehicleCommandPublisher.answerOf(
                "fleet-01", RESPONSES, response("bike-0042", "lock", "FAILED", null)))
        .map(MqttVehicleCommandPublisher.Answer::result)
        .contains(new LockResult.Refused("FAILED"));
  }

  /** Contract: the topic and the payload agree, or the message is rejected. */
  @Test
  void answerOf_vehicleOfThePayloadNotTheOneOfTheTopic_isIgnored() {
    assertThat(
            MqttVehicleCommandPublisher.answerOf(
                "fleet-01", RESPONSES, response("bike-0001", "lock", "COMPLETED", null)))
        .isEmpty();
  }

  @Test
  void answerOf_answerToAnotherCommand_isIgnored() {
    assertThat(
            MqttVehicleCommandPublisher.answerOf(
                "fleet-01", RESPONSES, response("bike-0042", "unlock", "COMPLETED", null)))
        .isEmpty();
  }

  @Test
  void answerOf_notAResponse_isIgnored() {
    assertThat(
            MqttVehicleCommandPublisher.answerOf(
                "fleet-01", RESPONSES, "not json".getBytes(StandardCharsets.UTF_8)))
        .isEmpty();
    assertThat(
            MqttVehicleCommandPublisher.answerOf(
                "fleet-01", RESPONSES, "{\"type\":\"telemetry\"}".getBytes(StandardCharsets.UTF_8)))
        .isEmpty();
  }

  @Test
  void new_responseTimeoutThatIsNotPositive_isRefused() {
    Clock clock = Clock.systemUTC();
    Duration ttl = Duration.ofSeconds(30);

    assertThatThrownBy(() ->
            new MqttVehicleCommandPublisher(
                OFFLINE, "fleet-01", ttl, Duration.ZERO, clock, UUID::randomUUID))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
