package pt.ua.deti.tqs.p11.domain.fleet;

/** A WGS84 position in decimal degrees, as in the telemetry contract. */
public record GeoPosition(double latitude, double longitude) {

  public GeoPosition {
    // Validation not implemented yet (KAN-81).
  }
}
