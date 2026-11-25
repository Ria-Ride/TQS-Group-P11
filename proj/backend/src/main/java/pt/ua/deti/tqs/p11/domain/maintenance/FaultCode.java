package pt.ua.deti.tqs.p11.domain.maintenance;

/**
 * The code of a vehicle fault, as in the telemetry contract: UPPER_SNAKE_CASE, starting with a
 * letter, up to 64 characters (e.g. BRAKE_WEAR). The contract does not list the codes: any code in
 * that format is accepted.
 */
public record FaultCode(String value) {

  public static FaultCode of(String value) {
    throw new UnsupportedOperationException("US10");
  }
}
