package pt.ua.deti.tqs.p11.domain.fleet;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The code that identifies a hub or a vehicle, given by the operator (e.g. hub-03, bike-0009). It
 * follows the identifier format of the telemetry contract, so the same code can be used in MQTT
 * topics and matched with the simulator's vehicles and hubs (US09).
 */
public record FleetCode(String value) {

  /** Letters, digits, '-' and '_', 1 to 64 characters: no '/', '+' or '#' (MQTT wildcards). */
  private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,64}");

  public FleetCode {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "A code has 1 to 64 letters, digits, '-' or '_': '%s'".formatted(value));
    }
  }

  public static FleetCode of(String value) {
    return new FleetCode(value);
  }
}
