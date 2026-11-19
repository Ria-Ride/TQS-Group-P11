package pt.ua.deti.tqs.p11.domain.fleet;

/**
 * The code that identifies a hub or a vehicle, given by the operator (e.g. hub-03, bike-0009). It
 * follows the identifier format of the telemetry contract, so the same code can be used in MQTT
 * topics and matched with the simulator's vehicles and hubs (US09).
 */
public record FleetCode(String value) {

  public FleetCode {
    // Validation not implemented yet (KAN-80).
  }

  public static FleetCode of(String value) {
    return new FleetCode(value);
  }
}
