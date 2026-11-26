package pt.ua.deti.tqs.p11.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/** BR4: when a Pass is valid, which vehicle types it allows, and until when it covers a ride. */
class PassCoverageTest {

  private static final Instant FROM = Instant.parse("2026-11-26T09:00:00Z");
  private static final Instant TO = Instant.parse("2026-11-27T09:00:00Z");
  private static final PassCoverage BIKES_ONLY =
      new PassCoverage(UUID.randomUUID(), FROM, TO, Set.of(VehicleType.E_BIKE));

  @Test
  void isValidAt_fromTheStartIncluded() {
    assertThat(BIKES_ONLY.isValidAt(FROM)).isTrue();
    assertThat(BIKES_ONLY.isValidAt(FROM.minusSeconds(1))).isFalse();
  }

  @Test
  void isValidAt_untilTheEndExcluded() {
    assertThat(BIKES_ONLY.isValidAt(TO.minusSeconds(1))).isTrue();
    assertThat(BIKES_ONLY.isValidAt(TO)).isFalse();
  }

  @Test
  void allows_onlyItsVehicleTypes() {
    assertThat(BIKES_ONLY.allows(VehicleType.E_BIKE)).isTrue();
    assertThat(BIKES_ONLY.allows(VehicleType.E_SCOOTER)).isFalse();
  }

  /** A ride may end exactly when the Pass ends. */
  @Test
  void coversUntil_theEndOfThePassIncluded() {
    assertThat(BIKES_ONLY.coversUntil(TO)).isTrue();
    assertThat(BIKES_ONLY.coversUntil(TO.plusSeconds(1))).isFalse();
  }

  @Test
  void new_withoutVehicleTypes_isRefused() {
    UUID id = UUID.randomUUID();
    Set<VehicleType> none = Set.of();

    assertThatThrownBy(() -> new PassCoverage(id, FROM, TO, none))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
