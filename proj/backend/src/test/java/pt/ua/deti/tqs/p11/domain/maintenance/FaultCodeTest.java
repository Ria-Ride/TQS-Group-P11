package pt.ua.deti.tqs.p11.domain.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Fault codes follow the telemetry contract: UPPER_SNAKE_CASE, starting with a letter. The contract
 * does not list them, so any code in that format is accepted (an unknown one is a generic fault).
 */
class FaultCodeTest {

  @ParameterizedTest
  @ValueSource(strings = {"BRAKE_WEAR", "LOW_TYRE_PRESSURE", "E42", "X"})
  void of_upperSnakeCase_isAccepted(String value) {
    assertThat(FaultCode.of(value).value()).isEqualTo(value);
  }

  @Test
  void of_64Characters_isAccepted() {
    String longest = "A".repeat(64);

    assertThat(FaultCode.of(longest).value()).isEqualTo(longest);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "brake_wear", "Brake_Wear", "42_ERROR", "_BRAKE", "BRAKE-WEAR", "A B"})
  void of_notUpperSnakeCase_isRejected(String value) {
    assertThatThrownBy(() -> FaultCode.of(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void of_65Characters_isRejected() {
    String tooLong = "A".repeat(65);

    assertThatThrownBy(() -> FaultCode.of(tooLong)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void of_null_isRejected() {
    assertThatThrownBy(() -> FaultCode.of(null)).isInstanceOf(NullPointerException.class);
  }
}
