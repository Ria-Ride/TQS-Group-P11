package pt.ua.deti.tqs.p11.domain.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Percentage.withPercentage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Latitude from -90 to 90 and longitude from -180 to 180, both inclusive. */
class GeoPositionTest {

  @ParameterizedTest(name = "({0}, {1}) accepted")
  @CsvSource({"40.6405, -8.6538", "90, 0", "-90, 0", "0, 180", "0, -180"})
  void new_positionOnTheGlobe_keepsTheCoordinates(double latitude, double longitude) {
    GeoPosition position = new GeoPosition(latitude, longitude);

    assertThat(position.latitude()).isEqualTo(latitude);
    assertThat(position.longitude()).isEqualTo(longitude);
  }

  @ParameterizedTest(name = "({0}, {1}) refused")
  @CsvSource({
    "90.0001,   0",
    "-90.0001,  0",
    "0,         180.0001",
    "0,         -180.0001",
    "NaN,       0",
    "0,         NaN",
    "Infinity,  0"
  })
  void new_positionOutsideTheGlobe_isRejected(double latitude, double longitude) {
    assertThatThrownBy(() -> new GeoPosition(latitude, longitude))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** US06: nearby hubs are ordered by distance along the Earth's surface (Haversine). */
  @ParameterizedTest(name = "{0},{1} to {2},{3}: about {4} m")
  @CsvSource({
    "40.6405, -8.6538, 40.6405, -8.6538, 0",
    // 0.0027 degrees of latitude is about 300 m anywhere
    "40.6405, -8.6538, 40.6432, -8.6538, 300",
    // Aveiro to Porto, about 56 km
    "40.6405, -8.6538, 41.1579, -8.6291, 57500",
    // east-west only: 0.01 degrees of longitude at 40.6 N is about 843 m
    "40.6405, -8.6538, 40.6405, -8.6438, 843",
    // Aveiro to Lisbon, both coordinates change: about 217 km
    "40.6405, -8.6538, 38.7223, -9.1393, 217000"
  })
  void distanceInMetersTo_isTheGreatCircleDistance(
      double lat1, double lon1, double lat2, double lon2, double metres) {
    double distance = new GeoPosition(lat1, lon1).distanceInMetersTo(new GeoPosition(lat2, lon2));

    assertThat(distance).isCloseTo(metres, withPercentage(1));
  }

  @Test
  void distanceInMetersTo_isTheSameBothWays() {
    GeoPosition hub03 = new GeoPosition(40.6405, -8.6538);
    GeoPosition hub09 = new GeoPosition(40.6675, -8.6538);

    assertThat(hub03.distanceInMetersTo(hub09)).isEqualTo(hub09.distanceInMetersTo(hub03));
  }
}
