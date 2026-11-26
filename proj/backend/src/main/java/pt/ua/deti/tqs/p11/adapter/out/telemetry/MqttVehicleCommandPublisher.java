package pt.ua.deti.tqs.p11.adapter.out.telemetry;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCommandPort;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;

/**
 * VehicleCommandPort over MQTT, as the simulator contract 1.0.0 defines commands: published on
 * fleet/{fleetId}/vehicles/{vehicleId}/commands/{command}, QoS 1, not retained, with a new
 * messageId and commandId each time and an expiresAt, so a command delayed in the broker is
 * rejected by the vehicle instead of applied late. The answer, on the responses topic, is not read
 * here: the platform does not wait for it (US04); reading it comes with the lock command (US06).
 *
 * <p>The message is written by hand: every value is a UUID, a UTC instant or a contract identifier
 * ([A-Za-z0-9_-]), so none needs escaping inside a JSON string.
 */
public class MqttVehicleCommandPublisher implements VehicleCommandPort {

  private static final Logger LOG = LoggerFactory.getLogger(MqttVehicleCommandPublisher.class);
  private static final String SCHEMA_VERSION = "1.0.0";
  private static final String UNLOCK = "unlock";

  private final Mqtt5AsyncClient client;
  private final String fleetId;
  private final Duration ttl;
  private final Clock clock;
  private final Supplier<UUID> ids;

  /**
   * @param ttl how long after it is sent a command is still valid at the vehicle
   * @throws IllegalArgumentException if fleetId is not a contract identifier or ttl is not positive
   */
  public MqttVehicleCommandPublisher(
      Mqtt5AsyncClient client, String fleetId, Duration ttl, Clock clock, Supplier<UUID> ids) {
    this.client = Objects.requireNonNull(client, "client");
    this.fleetId = FleetCode.of(fleetId).value();
    this.ttl = Objects.requireNonNull(ttl, "ttl");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.ids = Objects.requireNonNull(ids, "ids");
    if (ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("A command must stay valid for some time: " + ttl);
    }
  }

  @Override
  public void unlock(FleetCode vehicle) {
    Instant now = clock.instant();
    UUID messageId = ids.get();
    UUID commandId = ids.get();
    String topic = unlockTopic(fleetId, vehicle);
    byte[] message =
        unlockMessage(fleetId, vehicle, messageId, commandId, now, now.plus(ttl))
            .getBytes(StandardCharsets.UTF_8);
    try {
      client
          .publishWith()
          .topic(topic)
          .qos(MqttQos.AT_LEAST_ONCE)
          .retain(false)
          .payload(message)
          .send()
          .whenComplete((sent, error) -> {
            if (error != null) {
              LOG.warn(
                  "Unlock {} of {} not sent: {}", commandId, vehicle.value(), error.getMessage());
            } else {
              LOG.info("Unlock {} sent to {}", commandId, vehicle.value());
            }
          });
    } catch (RuntimeException e) {
      LOG.warn("Unlock {} of {} not sent: {}", commandId, vehicle.value(), e.getMessage());
    }
  }

  static String unlockTopic(String fleetId, FleetCode vehicle) {
    return "fleet/%s/vehicles/%s/commands/%s".formatted(fleetId, vehicle.value(), UNLOCK);
  }

  /** The command message of the contract, with its fields in the contract's order. */
  static String unlockMessage(
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
