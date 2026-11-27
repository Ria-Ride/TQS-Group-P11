package pt.ua.deti.tqs.p11.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/**
 * BR13: the charge is taken from the deposit, never more than the deposit, and the rest is given
 * back. The first two rows are the acceptance criteria of US06.
 */
class SettlementTest {

  @ParameterizedTest(name = "charge {0} of deposit {1}: capture {2}, release {3}")
  @CsvSource({
    "4.00,  30.00, 4.00,  26.00",
    "34.00, 30.00, 30.00, 0.00",
    "0.00,  30.00, 0.00,  30.00",
    "30.00, 30.00, 30.00, 0.00",
    "29.99, 30.00, 29.99, 0.01"
  })
  void of_capturesAtMostTheDepositAndReleasesTheRest(
      String charge, String deposit, String captured, String released) {
    Settlement settlement = Settlement.of(Money.of(charge), Money.of(deposit));

    assertThat(settlement.charge()).isEqualTo(Money.of(charge));
    assertThat(settlement.captured()).isEqualTo(Money.of(captured));
    assertThat(settlement.released()).isEqualTo(Money.of(released));
  }
}
