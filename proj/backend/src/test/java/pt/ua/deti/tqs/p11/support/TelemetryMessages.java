package pt.ua.deti.tqs.p11.support;

import java.util.UUID;

/** Telemetry messages of the simulator contract 1.0.0, as the simulator would publish them. */
public final class TelemetryMessages {

  public static final String FLEET = "fleet-01";

  private TelemetryMessages() {}

  /** A vehicle parked in a hub, at a position. battery is written as given (may be invalid). */
  public static String reading(
      String vehicleId, String battery, String lockState, String timestamp) {
    return """
    {"schemaVersion":"1.0.0","messageId":"%s","type":"telemetry","timestamp":"%s",
     "fleetId":"%s","vehicleId":"%s",
     "data":{"batteryPercent":%s,"lockState":"%s",
             "position":{"lat":40.6405,"lon":-8.6538},"hubId":"hub-03","faultCode":null}}
    """
        .formatted(UUID.randomUUID(), timestamp, FLEET, vehicleId, battery, lockState);
  }

  public static String reading(String vehicleId, String battery, String timestamp) {
    return reading(vehicleId, battery, "locked", timestamp);
  }
}
