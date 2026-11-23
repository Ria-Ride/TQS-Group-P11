package pt.ua.deti.tqs.p11.application.telemetry;

/**
 * Port: where the core receives telemetry (K1). Any simulator, or a real fleet, is a new adapter
 * that calls this; the core knows nothing of MQTT or of the message format.
 */
public interface TelemetryIngestionPort {

  /** Applies the reading to its vehicle, or discards it (BR17). */
  ReadingOutcome report(VehicleReading reading);
}
