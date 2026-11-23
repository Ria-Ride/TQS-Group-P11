package pt.ua.deti.tqs.p11.adapter.in.telemetry;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.deti.tqs.p11.application.telemetry.TelemetryIngestionPort;
import pt.ua.deti.tqs.p11.application.telemetry.VehicleReading;

/**
 * Adapter (in): listens to the telemetry of every vehicle of the fleet (QoS 1) and hands each valid
 * reading to the core through the TelemetryIngestionPort. A message that breaks the contract is
 * logged and dropped. It never throws out of a message: an exception thrown into the MQTT client's
 * callback could stop the subscription.
 */
public class MqttTelemetrySubscriber {

  private static final Logger LOG = LoggerFactory.getLogger(MqttTelemetrySubscriber.class);

  private final Mqtt5AsyncClient client;
  private final String fleetId;
  private final TelemetryMessageParser parser;
  private final TelemetryIngestionPort port;
  private final CompletableFuture<Void> subscribed = new CompletableFuture<>();

  public MqttTelemetrySubscriber(
      Mqtt5AsyncClient client,
      String fleetId,
      TelemetryMessageParser parser,
      TelemetryIngestionPort port) {
    this.client = client;
    this.fleetId = fleetId;
    this.parser = parser;
    this.port = port;
  }

  /** Completes when the broker has acknowledged the first subscription. */
  public CompletableFuture<Void> subscribed() {
    return subscribed;
  }

  /**
   * Subscribes to the telemetry of the whole fleet. Called on every (re)connection, so a broker
   * that restarted and forgot the subscription gets it again.
   */
  public void subscribe() {
    String filter = "fleet/%s/vehicles/+/telemetry".formatted(fleetId);
    client
        .subscribeWith()
        .topicFilter(filter)
        .qos(MqttQos.AT_LEAST_ONCE)
        .callback(message -> onMessage(message.getTopic().toString(), message.getPayloadAsBytes()))
        .send()
        .whenComplete((ack, error) -> {
          if (error == null) {
            LOG.info("Listening for telemetry on {}", filter);
            subscribed.complete(null);
          } else {
            LOG.error("Could not subscribe to {}: {}", filter, error.getMessage());
          }
        });
  }

  /** Handles one message from a telemetry topic. Never throws. */
  void onMessage(String topic, byte[] payload) {
    VehicleReading reading;
    try {
      reading = parser.parse(topic, new String(payload, StandardCharsets.UTF_8));
    } catch (InvalidTelemetryMessageException e) {
      LOG.warn("Telemetry message on {} discarded: {}", topic, e.getMessage());
      return;
    }
    try {
      port.report(reading);
    } catch (RuntimeException e) {
      LOG.error("Telemetry reading of {} could not be applied", reading.vehicleCode(), e);
    }
  }
}
