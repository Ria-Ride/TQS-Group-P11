package pt.ua.deti.tqs.p11.adapter.in.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.application.telemetry.ReadingOutcome;
import pt.ua.deti.tqs.p11.application.telemetry.TelemetryIngestionPort;
import pt.ua.deti.tqs.p11.application.telemetry.VehicleReading;

/**
 * What the MQTT adapter does with one message: a valid one goes to the core port, and nothing else
 * does. It never throws, because an exception in the MQTT client's callback could stop the
 * subscription. The subscription itself is tested against a real broker (TelemetryIngestionIT).
 */
class MqttTelemetrySubscriberTest {

  private static final String TOPIC = "fleet/fleet-01/vehicles/bike-0042/telemetry";
  private static final String VALID =
      """
      {"schemaVersion":"1.0.0","messageId":"7d9f2c4e-3b1a-4e55-9a60-0c1f8f6b1a01",
       "type":"telemetry","timestamp":"2026-11-10T10:00:00Z","fleetId":"fleet-01",
       "vehicleId":"bike-0042",
       "data":{"batteryPercent":78.5,"lockState":"locked",
               "position":{"lat":40.6405,"lon":-8.6538},"hubId":"hub-03","faultCode":null}}
      """;

  private final List<VehicleReading> reported = new ArrayList<>();

  private MqttTelemetrySubscriber subscriberWith(TelemetryIngestionPort port) {
    return new MqttTelemetrySubscriber(
        null, "fleet-01", new TelemetryMessageParser("fleet-01"), port);
  }

  private MqttTelemetrySubscriber recordingSubscriber() {
    return subscriberWith(reading -> {
      reported.add(reading);
      return ReadingOutcome.APPLIED;
    });
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void onMessage_validTelemetry_isReportedToTheCore() {
    recordingSubscriber().onMessage(TOPIC, bytes(VALID));

    assertThat(reported).hasSize(1);
    assertThat(reported.getFirst().batteryPercent()).isEqualTo(78.5);
  }

  @Test
  void onMessage_messageThatBreaksTheContract_isDroppedWithoutReachingTheCore() {
    recordingSubscriber().onMessage(TOPIC, bytes(VALID.replace("locked", "broken")));

    assertThat(reported).isEmpty();
  }

  @Test
  void onMessage_garbage_isDroppedWithoutThrowing() {
    assertThatCode(() -> recordingSubscriber().onMessage(TOPIC, bytes("not json")))
        .doesNotThrowAnyException();
    assertThat(reported).isEmpty();
  }

  @Test
  void onMessage_coreThatFails_doesNotThrowIntoTheClient() {
    MqttTelemetrySubscriber failing =
        subscriberWith(reading -> {
          throw new IllegalStateException("database down");
        });

    assertThatCode(() -> failing.onMessage(TOPIC, bytes(VALID))).doesNotThrowAnyException();
  }

  @Test
  void onMessage_discardedByTheCore_isNotAnError() {
    MqttTelemetrySubscriber discarding = subscriberWith(reading -> ReadingOutcome.UNKNOWN_VEHICLE);

    assertThatCode(() -> discarding.onMessage(TOPIC, bytes(VALID))).doesNotThrowAnyException();
  }
}
