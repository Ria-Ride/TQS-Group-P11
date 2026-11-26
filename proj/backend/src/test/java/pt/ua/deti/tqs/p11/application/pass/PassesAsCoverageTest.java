package pt.ua.deti.tqs.p11.application.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.booking.PassCoverage;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.pass.Pass;
import pt.ua.deti.tqs.p11.domain.pass.PassType;

/** BR4: the tourist's Passes as the booking rules see them, with what their types allow. */
@ExtendWith(MockitoExtension.class)
class PassesAsCoverageTest {

  private static final UUID ANA = UUID.randomUUID();
  private static final Instant FROM = Instant.parse("2026-11-26T09:00:00Z");
  private static final PassType BIKES =
      new PassType(
          UUID.randomUUID(), "BIKE_DAY", "Bike day", 1, Money.of("8.00"), Set.of(VehicleType.E_BIKE));
  private static final PassType ALL =
      new PassType(
          UUID.randomUUID(),
          "MULTI_DAY",
          "Multi-day",
          3,
          Money.of("25.00"),
          Set.of(VehicleType.E_BIKE, VehicleType.E_SCOOTER));

  @Mock private PassRepository passes;
  @Mock private PassTypeRepository types;

  private static Pass passOf(PassType type) {
    return Pass.issue(UUID.randomUUID(), ANA, type, null, type.basePrice(), FROM);
  }

  @Test
  void ofAccount_eachPassWithItsValidityAndTheVehicleTypesOfItsType() {
    Pass bikeDay = passOf(BIKES);
    Pass multiDay = passOf(ALL);
    when(passes.findByAccount(ANA)).thenReturn(List.of(bikeDay, multiDay));
    when(types.findAll()).thenReturn(List.of(BIKES, ALL));

    List<PassCoverage> coverages = new PassesAsCoverage(passes, types).ofAccount(ANA);

    assertThat(coverages)
        .containsExactly(
            new PassCoverage(
                bikeDay.id(), FROM, FROM.plus(Duration.ofDays(1)), Set.of(VehicleType.E_BIKE)),
            new PassCoverage(
                multiDay.id(),
                FROM,
                FROM.plus(Duration.ofDays(3)),
                Set.of(VehicleType.E_BIKE, VehicleType.E_SCOOTER)));
  }

  @Test
  void ofAccount_noPasses_isEmpty() {
    when(passes.findByAccount(ANA)).thenReturn(List.of());

    assertThat(new PassesAsCoverage(passes, types).ofAccount(ANA)).isEmpty();
  }

  /** Every pass points to a stored type (a foreign key); a missing one is a bug, not a refusal. */
  @Test
  void ofAccount_passOfAnUnknownType_isAnError() {
    when(passes.findByAccount(ANA)).thenReturn(List.of(passOf(BIKES)));
    when(types.findAll()).thenReturn(List.of(ALL));
    PassesAsCoverage coverage = new PassesAsCoverage(passes, types);

    assertThatThrownBy(() -> coverage.ofAccount(ANA)).isInstanceOf(IllegalStateException.class);
  }
}