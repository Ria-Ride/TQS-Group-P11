package pt.ua.deti.tqs.p11.support;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A real Mosquitto broker for integration and acceptance tests, the same version as the simulator's
 * own tests and the Compose file, plus a publisher that sends messages the way the simulator does
 * (QoS 1, retained).
 */
public final class MosquittoBroker {

  private static final int MQTT_PORT = 1883;

  private final GenericContainer<?> container =
      new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0.22"))
          .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
          .withExposedPorts(MQTT_PORT)
          .waitingFor(Wait.forListeningPort());

  private Mqtt5BlockingClient publisher;

  public void start() {
    container.start();
    publisher =
        Mqtt5Client.builder()
            .identifier("test-simulator-" + UUID.randomUUID())
            .serverHost(host())
            .serverPort(port())
            .buildBlocking();
    publisher.connect();
  }

  public void stop() {
    if (publisher != null) {
      publisher.disconnect();
    }
    container.stop();
  }

  public String host() {
    return container.getHost();
  }

  public int port() {
    return container.getMappedPort(MQTT_PORT);
  }

  /** Publishes as the simulator does: QoS 1 and retained. */
  public void publishTelemetry(String fleetId, String vehicleId, String json) {
    publisher
        .publishWith()
        .topic("fleet/%s/vehicles/%s/telemetry".formatted(fleetId, vehicleId))
        .qos(MqttQos.AT_LEAST_ONCE)
        .retain(true)
        .payload(json.getBytes(StandardCharsets.UTF_8))
        .send();
  }
}
