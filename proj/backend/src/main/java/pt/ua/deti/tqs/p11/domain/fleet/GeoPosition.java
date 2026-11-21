package pt.ua.deti.tqs.p11.domain.fleet;

/** A WGS84 position in decimal degrees, as in the telemetry contract. */
public record GeoPosition(double latitude, double longitude) {

  private static final double MAX_LATITUDE = 90;
  private static final double MAX_LONGITUDE = 180;

  public GeoPosition {
    // Written as "not inside" so that NaN, which fails every comparison, is rejected too.
    if (!(Math.abs(latitude) <= MAX_LATITUDE)) {
      throw new IllegalArgumentException(
          "Latitude must be between -90 and 90: %s".formatted(latitude));
    }
    if (!(Math.abs(longitude) <= MAX_LONGITUDE)) {
      throw new IllegalArgumentException(
          "Longitude must be between -180 and 180: %s".formatted(longitude));
    }
  }
}
