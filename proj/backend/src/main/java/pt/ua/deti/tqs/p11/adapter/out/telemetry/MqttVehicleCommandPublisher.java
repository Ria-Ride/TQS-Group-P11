package pt.ua.deti.tqs.p11.adapter.out.telemetry;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.deti.tqs.p11.application.fleet.LockResult;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCommandPort;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * VehicleCommandPort over MQTT, as the simulator contract 1.0.0 defines commands: published on
 * fleet/{fleetId}/vehicles/{vehicleId}/commands/{command}, QoS 1, not retained, with a new
 * messageId and commandId each time and an expiresAt, so a command delayed in the broker is
 * rejected by the vehicle instead of applied late.
 *
 * <p>An unlock is not waited for (US04). A lock is (US06): the vehicle answers on
 * fleet/{fleetId}/vehicles/{vehicleId}/responses with the commandId it was sent, so each lock waits
 * for the answer with its own id, up to the response timeout (the contract recommends 10 s). The
 * subscription to the responses is made again on every connection ({@link #listenForResponses}).
 * Answers to other commands, or that break the contract (topic and payload disagree), are ignored.
 *
 * <p>The command is written by hand: every value is a UUID, a UTC instant or a contract identifier
 * ([A-Za-z0-9_-]), so none needs escaping inside a JSON string. Answers are read with Jackson.
 */
public class MqttVehicleCommandPublisher implements VehicleCommandPort {

  private static final Logger LOG = LoggerFactory.getLogger(MqttVehicleCommandPublisher.class);
  private static final String SCHEMA_VERSION = "1.0.0";
  private static final String UNLOCK = "unlock";
  private static final String LOCK = "lock";
  private static final String COMPLETED = "COMPLETED";
  private static final Duration CONTRACT_RESPONSE_TIMEOUT = Duration.ofSeconds(10);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** A vehicle's answer to one command. */
  record Answer(UUID commandId, LockResult result) {}

  private final Mqtt5AsyncClient client;
  private final String fleetId;
  private final Duration ttl;
  private final Duration responseTimeout;
  private final Clock clock;
  private final Supplier<UUID> ids;
  private final Map<UUID, CompletableFuture<LockResult>> waiting = new ConcurrentHashMap<>();

  /** With the response timeout the contract recommends. */
  public MqttVehicleCommandPublisher(
      Mqtt5AsyncClient client, String fleetId, Duration ttl, Clock clock, Supplier<UUID> ids) {
    this(client, fleetId, ttl, CONTRACT_RESPONSE_TIMEOUT, clock, ids);
  }

  /**
   * @param ttl how long after it is sent a command is still valid at the vehicle
   * @param responseTimeout how long a lock waits for the vehicle's answer
   * @throws IllegalArgumentException if fleetId is not a contract identifier, or ttl or
   *     responseTimeout is not positive
   */
  public MqttVehicleCommandPublisher(
      Mqtt5AsyncClient client,
      String fleetId,
      Duration ttl,
      Duration responseTimeout,
      Clock clock,
      Supplier<UUID> ids) {
    this.client = Objects.requireNonNull(client, "client");
    this.fleetId = FleetCode.of(fleetId).value();
    this.ttl = Objects.requireNonNull(ttl, "ttl");
    this.responseTimeout = Objects.requireNonNull(responseTimeout, "responseTimeout");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.ids = Objects.requireNonNull(ids, "ids");
    if (ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("A command must stay valid for some time: " + ttl);
    }
    if (responseTimeout.isNegative() || responseTimeout.isZero()) {
      throw new IllegalArgumentException(
          "A lock must wait some time for its answer: " + responseTimeout);
    }
  }

  /**
   * Subscribes to the answers of every vehicle of the fleet. Called on every connection, since the
   * broker forgets subscriptions of a clean session.
   *
   * @return completes when the broker has acknowledged the subscription
   */
  public CompletableFuture<Void> listenForResponses() {
    String filter = "fleet/%s/vehicles/+/responses".formatted(fleetId);
    return client
        .subscribeWith()
        .topicFilter(filter)
        .qos(MqttQos.AT_LEAST_ONCE)
        .callback(message -> onResponse(message.getTopic().toString(), message.getPayloadAsBytes()))
        .send()
        .whenComplete((ack, error) -> {
          if (error != null) {
            LOG.warn("Not subscribed to {}: {}", filter, error.getMessage());
          }
        })
        .thenApply(ack -> null);
  }

  @Override
  public void unlock(FleetCode vehicle) {
    UUID commandId = ids.get();
    publish(vehicle, UNLOCK, commandId)
        .whenComplete((sent, error) -> {
          if (error != null) {
            LOG.warn(
                "Unlock {} of {} not sent: {}", commandId, vehicle.value(), error.getMessage());
          } else {
            LOG.info("Unlock {} sent to {}", commandId, vehicle.value());
          }
        });
  }

  @Override
  public LockResult lock(FleetCode vehicle) {
    UUID commandId = ids.get();
    CompletableFuture<LockResult> answer = new CompletableFuture<>();
    waiting.put(commandId, answer);
    try {
      publish(vehicle, LOCK, commandId).get(responseTimeout.toMillis(), TimeUnit.MILLISECONDS);
      LockResult result = answer.get(responseTimeout.toMillis(), TimeUnit.MILLISECONDS);
      LOG.info("Lock {} of {}: {}", commandId, vehicle.value(), result);
      return result;
    } catch (TimeoutException | ExecutionException e) {
      LOG.warn("Lock {} of {} got no answer: {}", commandId, vehicle.value(), e.getMessage());
      return new LockResult.NoAnswer();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new LockResult.NoAnswer();
    } finally {
      waiting.remove(commandId);
    }
  }

  /** Never throws: a broken publish shows as a failed future, as the client's own does. */
  private CompletableFuture<?> publish(FleetCode vehicle, String command, UUID commandId) {
    Instant now = clock.instant();
    byte[] message =
        commandMessage(fleetId, vehicle, ids.get(), commandId, now, now.plus(ttl))
            .getBytes(StandardCharsets.UTF_8);
    try {
      return client
          .publishWith()
          .topic(commandTopic(fleetId, vehicle, command))
          .qos(MqttQos.AT_LEAST_ONCE)
          .retain(false)
          .payload(message)
          .send();
    } catch (RuntimeException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private void onResponse(String topic, byte[] payload) {
    answerOf(fleetId, topic, payload)
        .ifPresent(answer -> {
          CompletableFuture<LockResult> waitingFor = waiting.get(answer.commandId());
          if (waitingFor != null) {
            waitingFor.complete(answer.result());
          }
        });
  }

  /**
   * The answer to a lock in a responses message, or empty if it is not one: not JSON, not a
   * response, not about a lock, or a topic and payload that disagree (contract rule).
   */
  static Optional<Answer> answerOf(String fleetId, String topic, byte[] payload) {
    try {
      JsonNode message = JSON.readTree(payload);
      JsonNode data = message.get("data");
      String vehicle = text(message, "vehicleId");
      if (!"response".equals(text(message, "type"))
          || !fleetId.equals(text(message, "fleetId"))
          || vehicle == null
          || !topic.equals("fleet/%s/vehicles/%s/responses".formatted(fleetId, vehicle))
          || data == null
          || !LOCK.equals(text(data, "command"))) {
        return Optional.empty();
      }
      String commandId = text(data, "commandId");
      String status = text(data, "status");
      if (commandId == null || status == null) {
        return Optional.empty();
      }
      String reason = text(data, "reason");
      LockResult result =
          COMPLETED.equals(status)
              ? new LockResult.Locked()
              : new LockResult.Refused(reason != null ? reason : status);
      return Optional.of(new Answer(UUID.fromString(commandId), result));
    } catch (JacksonException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isString() ? value.asString() : null;
  }

  static String unlockTopic(String fleetId, FleetCode vehicle) {
    return commandTopic(fleetId, vehicle, UNLOCK);
  }

  static String commandTopic(String fleetId, FleetCode vehicle, String command) {
    return "fleet/%s/vehicles/%s/commands/%s".formatted(fleetId, vehicle.value(), command);
  }

  /** The command message of the contract, with its fields in the contract's order. */
  static String unlockMessage(
      String fleetId,
      FleetCode vehicle,
      UUID messageId,
      UUID commandId,
      Instant sentAt,
      Instant expiresAt) {
    return commandMessage(fleetId, vehicle, messageId, commandId, sentAt, expiresAt);
  }

  /** Lock, unlock and ping carry the same fields; the command is named by the topic. */
  static String commandMessage(
      String fleetId,
      FleetCode vehicle,
      UUID messageId,
      UUID commandId,
      Instant sentAt,
      Instant expiresAt) {
    return ("{\"schemaVersion\":\"%s\",\"messageId\":\"%s\",\"type\":\"command\","
            + "\"timestamp\":\"%s\",\"fleetId\":\"%s\",\"vehicleId\":\"%s\","
            + "\"data\":{\"commandId\":\"%s\",\"expiresAt\":\"%s\"}}")
        .formatted(
            SCHEMA_VERSION, messageId, sentAt, fleetId, vehicle.value(), commandId, expiresAt);
  }
}
