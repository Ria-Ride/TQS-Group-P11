package pt.ua.deti.tqs.p11.support;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The vehicles of the acceptance tests, on the MQTT broker, as the simulator answers commands
 * (simulator contract 1.0.0): every lock command gets a response with its commandId. By default the
 * answer is COMPLETED; a scenario can make a vehicle reject the lock with a reason, or stay silent.
 * It also remembers which vehicles received a lock.
 */
public final class SimulatedVehicles {

  /** How a vehicle answers the next lock commands. */
  public sealed interface Answer {
    record Completed() implements Answer {}

    record Rejected(String reason) implements Answer {}

    record Silent() implements Answer {}
  }

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Mqtt5AsyncClient client;
  private final Map<String, Answer> answers = new ConcurrentHashMap<>();
  private final List<String> locked = new CopyOnWriteArrayList<>();

  /** Connects and listens to lock commands of every fleet. */
  public SimulatedVehicles(String host, int port) {
    client =
        Mqtt5Client.builder()
            .identifier("simulated-vehicles-" + UUID.randomUUID())
            .serverHost(host)
            .serverPort(port)
            .buildAsync();
    client.connect().join();
    client
        .subscribeWith()
        .topicFilter("fleet/+/vehicles/+/commands/lock")
        .qos(MqttQos.AT_LEAST_ONCE)
        .callback(message -> answer(message.getTopic().toString(), message.getPayloadAsBytes()))
        .send()
        .join();
  }

  /** Every vehicle answers COMPLETED again, and no lock is remembered. */
  public void reset() {
    answers.clear();
    locked.clear();
  }

  public void willAnswer(String vehicle, Answer answer) {
    answers.put(vehicle, answer);
  }

  /** The vehicles that received a lock since the last reset, in order. */
  public List<String> lockedVehicles() {
    return List.copyOf(locked);
  }

  private void answer(String topic, byte[] payload) {
    String[] parts = topic.split("/");
    String fleet = parts[1];
    String vehicle = parts[3];
    locked.add(vehicle);
    Answer answer = answers.getOrDefault(vehicle, new Answer.Completed());
    if (answer instanceof Answer.Silent) {
      return;
    }
    JsonNode command = JSON.readTree(payload);
    String commandId = command.get("data").get("commandId").asString();
    String status = answer instanceof Answer.Rejected ? "REJECTED" : "COMPLETED";
    String reason =
        answer instanceof Answer.Rejected rejected ? "\"" + rejected.reason() + "\"" : "null";
    String response =
        ("{\"schemaVersion\":\"1.0.0\",\"messageId\":\"%s\",\"type\":\"response\","
                + "\"timestamp\":\"2026-11-26T10:00:00Z\",\"fleetId\":\"%s\",\"vehicleId\":\"%s\","
                + "\"data\":{\"commandId\":\"%s\",\"command\":\"lock\",\"status\":\"%s\","
                + "\"reason\":%s}}")
            .formatted(UUID.randomUUID(), fleet, vehicle, commandId, status, reason);
    client
        .publishWith()
        .topic("fleet/%s/vehicles/%s/responses".formatted(fleet, vehicle))
        .qos(MqttQos.AT_LEAST_ONCE)
        .payload(response.getBytes(StandardCharsets.UTF_8))
        .send();
  }
}
