package pt.ua.deti.tqs.p11.application.telemetry;

/** What happened to a telemetry reading (BR17). */
public enum ReadingOutcome {
  APPLIED,
  INVALID_BATTERY,
  UNKNOWN_VEHICLE,
  STALE
}
