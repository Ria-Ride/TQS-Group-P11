package pt.ua.deti.tqs.p11.domain.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The discount a partner hotel gives its guests (BR9) is a percentage from 0 to 100. */
class PartnerAccommodationTest {

  @ParameterizedTest
  @ValueSource(ints = {0, 100})
  void new_discountAtTheLimits_isAccepted(int percent) {
    PartnerAccommodation hotel = new PartnerAccommodation(UUID.randomUUID(), "Hotel", percent);

    assertThat(hotel.discountPercent()).isEqualTo(percent);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 101})
  void new_discountOutsideZeroToHundred_isRefused(int percent) {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(() -> new PartnerAccommodation(id, "Hotel", percent))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
