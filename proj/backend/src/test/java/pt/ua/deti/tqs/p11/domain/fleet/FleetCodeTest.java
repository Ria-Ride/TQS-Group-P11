package pt.ua.deti.tqs.p11.domain.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Codes follow the identifier format of the telemetry contract: [A-Za-z0-9_-], 1 to 64 chars. */
class FleetCodeTest {

  @ParameterizedTest
  @ValueSource(strings = {"bike-0009", "hub_03", "A", "9"})
  void of_validCode_keepsTheValue(String value) {
    assertThat(FleetCode.of(value).value()).isEqualTo(value);
  }

  @Test
  void of_64Characters_isAccepted() {
    String value = "a".repeat(64);

    assertThat(FleetCode.of(value).value()).isEqualTo(value);
  }

  @Test
  void of_65Characters_isRejected() {
    String value = "a".repeat(65);

    assertThatThrownBy(() -> FleetCode.of(value)).isInstanceOf(IllegalArgumentException.class);
  }

  /** '/', '+' and '#' would break an MQTT topic; spaces and accents are outside the format. */
  @ParameterizedTest
  @EmptySource
  @ValueSource(
      strings = {"bike/0009", "bike+0009", "bike#0009", "bike 0009", " bike-0009", "bicicleta-ção"})
  void of_invalidCode_isRejected(String value) {
    assertThatThrownBy(() -> FleetCode.of(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void of_null_isRejected() {
    assertThatThrownBy(() -> FleetCode.of(null)).isInstanceOf(NullPointerException.class);
  }
}
