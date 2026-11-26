package pt.ua.deti.tqs.p11.adapter.in.telemetry;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pt.ua.deti.tqs.p11.application.telemetry.VehicleReading;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.LockState;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Translates a telemetry message of the simulator contract (1.0.0) into a core VehicleReading.
 * Everything about the wire format is here: topic, envelope, field names, units. A message that
 * breaks the contract is refused with InvalidTelemetryMessageException; one that follows it is
 * passed on whatever it says, so that BR17 (battery range, known vehicle, newer timestamp) is
 * decided by the core and not by the format. Fields added by a newer minor version are ignored.
 */
public class TelemetryMessageParser {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
  private static final Pattern TELEMETRY_TOPIC =
      Pattern.compile("^fleet/([^/]+)/vehicles/([^/]+)/telemetry$");
  private static final Pattern MAJOR_VERSION_1 = Pattern.compile("^1\\.\\d+\\.\\d+$");

  /**
   * The format of the core (UPPER_SNAKE_CASE, up to 64 characters), so FaultCode.of never fails.
   */
  private static final Pattern FAULT_CODE = Pattern.compile(FaultCode.FORMAT);

  private final String fleetId;

  /**
   * @param fleetId the only fleet this platform listens to
   * @throws IllegalArgumentException if it is not a valid contract identifier
   */
  public TelemetryMessageParser(String fleetId) {
    if (fleetId == null || !IDENTIFIER.matcher(fleetId).matches()) {
      throw new IllegalArgumentException("Not a valid fleet identifier: " + fleetId);
    }
    this.fleetId = fleetId;
  }

  public VehicleReading parse(String topic, String payload) {
    Matcher topicParts = TELEMETRY_TOPIC.matcher(topic);
    if (!topicParts.matches()) {
      throw invalid("not a telemetry topic");
    }
    JsonNode message = readObject(payload);

    if (!"telemetry".equals(text(message, "type"))) {
      throw invalid("type is not telemetry");
    }
    String version = text(message, "schemaVersion");
    if (version == null || !MAJOR_VERSION_1.matcher(version).matches()) {
      throw invalid("schemaVersion is missing or not 1.x.y");
    }
    if (!fleetId.equals(topicParts.group(1)) || !fleetId.equals(text(message, "fleetId"))) {
      throw invalid(
          "fleet of the topic and of the payload are not the platform's fleet " + fleetId);
    }
    String vehicleId = text(message, "vehicleId");
    if (!topicParts.group(2).equals(vehicleId)) {
      throw invalid("vehicle of the topic and of the payload differ");
    }
    Instant timestamp = timestamp(message);

    JsonNode data = message.get("data");
    if (data == null || !data.isObject()) {
      throw invalid("data is missing");
    }
    double battery = battery(data);
    LockState lock = lockState(data);
    GeoPosition position = position(data);
    String hubId = nullableText(data, "hubId");
    String faultCode = nullableText(data, "faultCode");
    if (position == null && hubId == null) {
      throw invalid("neither position nor hubId is known");
    }
    if (hubId != null && !IDENTIFIER.matcher(hubId).matches()) {
      throw invalid("hubId is not a valid identifier");
    }
    if (faultCode != null && !FAULT_CODE.matcher(faultCode).matches()) {
      throw invalid("faultCode is not UPPER_SNAKE_CASE of up to 64 characters");
    }
    try {
      return new VehicleReading(
          FleetCode.of(vehicleId),
          battery,
          lock,
          position,
          timestamp,
          faultCode == null ? null : FaultCode.of(faultCode));
    } catch (IllegalArgumentException e) {
      throw invalid("vehicleId is not a valid identifier");
    }
  }

  // ------------------------------------------------------------- fields

  private static JsonNode readObject(String payload) {
    try {
      JsonNode node = JSON.readTree(payload);
      if (node == null || !node.isObject()) {
        throw invalid("payload is not a JSON object");
      }
      return node;
    } catch (JacksonException e) {
      throw invalid("payload is not valid JSON");
    }
  }

  private static Instant timestamp(JsonNode message) {
    String text = text(message, "timestamp");
    if (text == null) {
      throw invalid("timestamp is missing");
    }
    // Instant.parse also takes offsets such as +01:00; the contract wants UTC with the Z suffix.
    if (!text.endsWith("Z")) {
      throw invalid("timestamp is not UTC ISO 8601 with Z");
    }
    try {
      return Instant.parse(text);
    } catch (DateTimeParseException e) {
      throw invalid("timestamp is not UTC ISO 8601 with Z");
    }
  }

  private static double battery(JsonNode data) {
    JsonNode battery = data.get("batteryPercent");
    if (battery == null || !battery.isNumber()) {
      throw invalid("batteryPercent is not a number");
    }
    return battery.asDouble();
  }

  private static LockState lockState(JsonNode data) {
    String lock = text(data, "lockState");
    if ("locked".equals(lock)) {
      return LockState.LOCKED;
    }
    if ("unlocked".equals(lock)) {
      return LockState.UNLOCKED;
    }
    throw invalid("lockState is not locked or unlocked");
  }

  /** The position, or null when the vehicle is known only by its hub. */
  private static GeoPosition position(JsonNode data) {
    if (!data.has("position")) {
      throw invalid("position is missing (it may be null)");
    }
    JsonNode position = data.get("position");
    if (position.isNull()) {
      return null;
    }
    JsonNode lat = position.get("lat");
    JsonNode lon = position.get("lon");
    if (lat == null || lon == null || !lat.isNumber() || !lon.isNumber()) {
      throw invalid("position needs numeric lat and lon");
    }
    try {
      return new GeoPosition(lat.asDouble(), lon.asDouble());
    } catch (IllegalArgumentException e) {
      throw invalid("position is outside the globe");
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isString() ? value.asString() : null;
  }

  /** A required field that may be null: missing or of another type is an error. */
  private static String nullableText(JsonNode data, String field) {
    if (!data.has(field)) {
      throw invalid(field + " is missing (it may be null)");
    }
    JsonNode value = data.get(field);
    if (value.isNull()) {
      return null;
    }
    if (!value.isString()) {
      throw invalid(field + " is not a string");
    }
    return value.asString();
  }

  private static InvalidTelemetryMessageException invalid(String reason) {
    return new InvalidTelemetryMessageException(reason);
  }
}
