package pt.ua.deti.tqs.p11.domain.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/** Issuing a pass after an approved payment (BR8, BR9). */
class PassTest {

  private static final Instant PAID_AT = Instant.parse("2026-11-24T10:15:30Z");
  private static final UUID ANA = UUID.randomUUID();
  private static final PassType MULTI_DAY =
      new PassType(
          UUID.randomUUID(),
          "MULTI_DAY",
          "Multi-day pass",
          3,
          Money.of("25.00"),
          Set.of(VehicleType.E_BIKE));

  @Test
  void issue_isValidFromThePaymentForTheDaysOfItsType() {
    Pass pass = Pass.issue(UUID.randomUUID(), ANA, MULTI_DAY, null, Money.of("25.00"), PAID_AT);

    assertThat(pass.validFrom()).isEqualTo(PAID_AT);
    assertThat(pass.validTo()).isEqualTo(Instant.parse("2026-11-27T10:15:30Z"));
  }

  @Test
  void issue_keepsTheAccountTheTypeTheStayAndThePricePaid() {
    UUID stayId = UUID.randomUUID();

    Pass pass = Pass.issue(UUID.randomUUID(), ANA, MULTI_DAY, stayId, Money.of("22.50"), PAID_AT);

    assertThat(pass.accountId()).isEqualTo(ANA);
    assertThat(pass.passTypeId()).isEqualTo(MULTI_DAY.id());
    assertThat(pass.passTypeCode()).isEqualTo("MULTI_DAY");
    assertThat(pass.stayId()).contains(stayId);
    assertThat(pass.price()).isEqualTo(Money.of("22.50"));
  }

  @Test
  void issue_withoutAStay_hasNoStay() {
    Pass pass = Pass.issue(UUID.randomUUID(), ANA, MULTI_DAY, null, Money.of("25.00"), PAID_AT);

    assertThat(pass.stayId()).isEmpty();
  }

  @Test
  void new_validToNotAfterValidFrom_isRefused() {
    UUID id = UUID.randomUUID();
    UUID typeId = MULTI_DAY.id();
    Money price = Money.of("25.00");

    assertThatThrownBy(() -> new Pass(id, ANA, typeId, "MULTI_DAY", null, price, PAID_AT, PAID_AT))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
