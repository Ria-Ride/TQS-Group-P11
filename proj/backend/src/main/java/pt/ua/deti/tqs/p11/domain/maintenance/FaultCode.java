package pt.ua.deti.tqs.p11.domain.maintenance;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The code of a vehicle fault, as in the telemetry contract: UPPER_SNAKE_CASE, starting with a
 * letter, up to 64 characters (e.g. BRAKE_WEAR). The contract does not list the codes: any code in
 * that format is accepted.
 */
public record FaultCode(String value) {

  /** Public so the API validates requests with the same rule. */
  public static final String FORMAT = "[A-Z][A-Z0-9_]{0,63}";

  private static final Pattern PATTERN = Pattern.compile(FORMAT);

  public FaultCode {
    Objects.requireNonNull(value, "value");
    if (!PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "A fault code is UPPER_SNAKE_CASE, starts with a letter and has up to 64 characters: '%s'"
              .formatted(value));
    }
  }

  public static FaultCode of(String value) {
    return new FaultCode(value);
  }
}
