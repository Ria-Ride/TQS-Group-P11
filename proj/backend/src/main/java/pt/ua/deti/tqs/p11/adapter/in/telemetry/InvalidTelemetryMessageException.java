package pt.ua.deti.tqs.p11.adapter.in.telemetry;

/** A message that breaks the simulator contract and cannot become a reading. */
public class InvalidTelemetryMessageException extends RuntimeException {

  public InvalidTelemetryMessageException(String message) {
    super(message);
  }
}
