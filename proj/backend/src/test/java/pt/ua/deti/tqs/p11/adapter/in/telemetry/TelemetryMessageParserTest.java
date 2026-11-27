package pt.ua.deti.tqs.p11.adapter.in.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pt.ua.deti.tqs.p11.application.telemetry.VehicleReading;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.LockState;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;

/**
 * Telemetry messages of the simulator contract 1.0.0, read from the examples of the contract
 * (copied to src/test/resources/telemetry). A message that breaks the contract is refused here,
 * before the core sees it; a battery outside 0 to 100 is judged by the core (BR17), so it passes.
 */
class TelemetryMessageParserTest {

  private static final String FLEET = "fleet-01";
  private static final TelemetryMessageParser PARSER = new TelemetryMessageParser(FLEET);

  private static String example(String folder, String name) throws IOException {
    try (InputStream in =
        TelemetryMessageParserTest.class.getResourceAsStream(
            "/telemetry/%s/%s.json".formatted(folder, name))) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String topicOf(String vehicleId) {
    return "fleet/%s/vehicles/%s/telemetry".formatted(FLEET, vehicleId);
  }

  // ------------------------------------------------------------- valid messages

  @Test
  void parse_vehicleInAHub_givesTheReading() throws IOException {
    VehicleReading reading =
        PARSER.parse(topicOf("bike-0042"), example("valid", "telemetry-in-hub"));

    assertThat(reading.vehicleCode()).isEqualTo(FleetCode.of("bike-0042"));
    assertThat(reading.batteryPercent()).isEqualTo(78.5);
    assertThat(reading.lockState()).isEqualTo(LockState.LOCKED);
    assertThat(reading.position()).isEqualTo(new GeoPosition(40.6405, -8.6538));
    assertThat(reading.timestamp()).isEqualTo(Instant.parse("2026-11-10T10:00:00Z"));
  }

  @Test
  void parse_vehicleMovingWithNoHub_givesTheReading() throws IOException {
    VehicleReading reading =
        PARSER.parse(topicOf("bike-0042"), example("valid", "telemetry-moving-no-hub"));

    assertThat(reading.position()).isNotNull();
    assertThat(reading.lockState()).isEqualTo(LockState.UNLOCKED);
    assertThat(reading.fault()).isEmpty();
  }

  /**
   * Only a hub is known: there is no position, and the core keeps the last known one. The fault is
   * passed on, so the core can open a ticket (US10).
   */
  @Test
  void parse_hubOnlyWithAFault_givesAReadingWithoutPositionAndWithTheFault() throws IOException {
    VehicleReading reading =
        PARSER.parse(topicOf("scooter-0007"), example("valid", "telemetry-hub-only-with-fault"));

    assertThat(reading.position()).isNull();
    assertThat(reading.vehicleCode()).isEqualTo(FleetCode.of("scooter-0007"));
    assertThat(reading.fault()).contains(FaultCode.of("BRAKE_WEAR"));
  }

  /** The core decides what a battery of -1 or 101 means (BR17), not the parser. */
  @Test
  void parse_batteryOutsideZeroToHundred_isPassedOnToTheCore() throws IOException {
    assertThat(
            PARSER
                .parse(topicOf("bike-0042"), example("invalid", "telemetry-battery-negative"))
                .batteryPercent())
        .isNegative();
    assertThat(
            PARSER
                .parse(topicOf("bike-0042"), example("invalid", "telemetry-battery-over-100"))
                .batteryPercent())
        .isGreaterThan(100);
  }

  @Test
  void parse_aFieldAddedByANewerMinorVersion_isIgnored() throws IOException {
    String message =
        example("valid", "telemetry-in-hub")
            .replace("\"speedKmh\": 0", "\"speedKmh\": 0, \"extra\": 1");

    assertThat(PARSER.parse(topicOf("bike-0042"), message).vehicleCode())
        .isEqualTo(FleetCode.of("bike-0042"));
  }

  // ------------------------------------------------------------- messages that break the contract

  static Stream<String> brokenExamples() {
    return Stream.of(
        "telemetry-bad-schema-version",
        "telemetry-missing-schema-version",
        "telemetry-wrong-envelope-type",
        "telemetry-timestamp-not-utc",
        "telemetry-unknown-lock-state",
        "telemetry-latitude-out-of-range",
        "telemetry-no-position-and-no-hub",
        "telemetry-fault-code-lowercase",
        "telemetry-vehicle-id-with-slash");
  }

  @ParameterizedTest
  @MethodSource("brokenExamples")
  void parse_messageThatBreaksTheContract_isRefused(String name) throws IOException {
    String message = example("invalid", name);

    assertThatThrownBy(() -> PARSER.parse(topicOf("bike-0042"), message))
        .isInstanceOf(InvalidTelemetryMessageException.class);
  }

  @Test
  void parse_vehicleOfTheTopicDiffersFromThePayload_isRefused() throws IOException {
    String message = example("valid", "telemetry-in-hub");

    assertThatThrownBy(() -> PARSER.parse(topicOf("bike-9999"), message))
        .isInstanceOf(InvalidTelemetryMessageException.class)
        .hasMessageContaining("vehicle");
  }

  @Test
  void parse_fleetOfTheTopicDiffersFromThePayload_isRefused() throws IOException {
    String message = example("valid", "telemetry-in-hub");

    assertThatThrownBy(() -> PARSER.parse("fleet/fleet-02/vehicles/bike-0042/telemetry", message))
        .isInstanceOf(InvalidTelemetryMessageException.class)
        .hasMessageContaining("fleet");
  }

  @Test
  void parse_otherFleetThanTheOneConfigured_isRefused() throws IOException {
    TelemetryMessageParser other = new TelemetryMessageParser("fleet-02");

    assertThatThrownBy(
            () -> other.parse(topicOf("bike-0042"), example("valid", "telemetry-in-hub")))
        .isInstanceOf(InvalidTelemetryMessageException.class);
  }

  @Test
  void parse_topicThatIsNotATelemetryTopic_isRefused() throws IOException {
    String message = example("valid", "telemetry-in-hub");

    assertThatThrownBy(() -> PARSER.parse("fleet/fleet-01/vehicles/bike-0042/events", message))
        .isInstanceOf(InvalidTelemetryMessageException.class);
  }

  @ParameterizedTest
  @MethodSource("notJsonObjects")
  void parse_payloadThatIsNotAJsonObject_isRefused(String payload) {
    assertThatThrownBy(() -> PARSER.parse(topicOf("bike-0042"), payload))
        .isInstanceOf(InvalidTelemetryMessageException.class);
  }

  static Stream<String> notJsonObjects() {
    return Stream.of("", "not json", "[]", "42", "null", "{\"type\": \"telemetry\"}", "{}");
  }

  @Test
  void parse_batteryThatIsNotANumber_isRefused() throws IOException {
    String message = example("valid", "telemetry-in-hub").replace("78.5", "\"high\"");

    assertThatThrownBy(() -> PARSER.parse(topicOf("bike-0042"), message))
        .isInstanceOf(InvalidTelemetryMessageException.class);
  }

  @Test
  void constructor_fleetThatIsNotAnIdentifier_isRefused() {
    assertThatThrownBy(() -> new TelemetryMessageParser("fleet/01"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
