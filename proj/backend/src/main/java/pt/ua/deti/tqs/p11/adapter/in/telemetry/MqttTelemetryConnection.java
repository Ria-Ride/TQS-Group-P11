package pt.ua.deti.tqs.p11.adapter.in.telemetry;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import pt.ua.deti.tqs.p11.application.telemetry.TelemetryIngestionPort;

/**
 * The connection of the telemetry adapter to the MQTT broker, tied to the life of the application:
 * it connects at start-up without waiting (a broker that is not up yet does not stop the platform)
 * and disconnects at shutdown. Automatic reconnect is on, with growing delays (1 s up to 2 min),
 * and the subscription is made again after every connection.
 */
public class MqttTelemetryConnection implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(MqttTelemetryConnection.class);

  private final Mqtt5AsyncClient client;
  private final MqttTelemetrySubscriber subscriber;
  private final String broker;
  private volatile boolean running;

  public MqttTelemetryConnection(
      String host,
      int port,
      String username,
      String password,
      String clientId,
      String fleetId,
      TelemetryIngestionPort ingestion) {
    this.broker = host + ":" + port;
    Mqtt5ClientBuilder builder =
        Mqtt5Client.builder()
            .identifier(clientId.isBlank() ? "platform-" + UUID.randomUUID() : clientId)
            .serverHost(host)
            .serverPort(port)
            .automaticReconnectWithDefaultConfig()
            .addConnectedListener(context -> onConnected());
    if (!username.isBlank()) {
      builder.simpleAuth(
          Mqtt5SimpleAuth.builder()
              .username(username)
              .password(password.getBytes(StandardCharsets.UTF_8))
              .build());
    }
    this.client = builder.buildAsync();
    this.subscriber =
        new MqttTelemetrySubscriber(
            client, fleetId, new TelemetryMessageParser(fleetId), ingestion);
  }

  public MqttTelemetrySubscriber subscriber() {
    return subscriber;
  }

  private void onConnected() {
    LOG.info("Connected to the MQTT broker at {}", broker);
    subscriber.subscribe();
  }

  @Override
  public void start() {
    running = true;
    client
        .connect()
        .whenComplete((ack, error) -> {
          if (error != null) {
            LOG.warn(
                "MQTT broker at {} not reachable yet, retrying: {}", broker, error.getMessage());
          }
        });
  }

  @Override
  public void stop() {
    running = false;
    client.disconnect();
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
