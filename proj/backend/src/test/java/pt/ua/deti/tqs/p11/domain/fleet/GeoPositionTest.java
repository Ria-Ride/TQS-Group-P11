package pt.ua.deti.tqs.p11.domain.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
