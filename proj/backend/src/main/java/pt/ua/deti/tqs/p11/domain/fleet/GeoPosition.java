package pt.ua.deti.tqs.p11.domain.fleet;

/** A WGS84 position in decimal degrees, as in the telemetry contract. */
public record GeoPosition(double latitude, double longitude) {

  private static final double MAX_LATITUDE = 90;
  private static final double MAX_LONGITUDE = 180;
  private static final double EARTH_RADIUS_METERS = 6_371_008.8;

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

  /**
   * US06: the distance along the Earth's surface (Haversine formula, mean Earth radius), as the
   * simulator measures trips. Good to a fraction of a percent over the distances between hubs.
   */
  public double distanceInMetersTo(GeoPosition other) {
    double lat1 = Math.toRadians(latitude);
    double lat2 = Math.toRadians(other.latitude);
    double halfLat = Math.sin((lat2 - lat1) / 2);
    double halfLon = Math.sin(Math.toRadians(other.longitude - longitude) / 2);
    double a = halfLat * halfLat + Math.cos(lat1) * Math.cos(lat2) * halfLon * halfLon;
    return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
  }
}
