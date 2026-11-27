package pt.ua.deti.tqs.p11.domain.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Euro amounts with cents, and the stay discount of BR9 rounded half-up to the cent. */
class MoneyTest {

  @Test
  void of_keepsTwoDecimals_soTenAndTenPointZeroZeroAreTheSameAmount() {
    assertThat(Money.of("10")).isEqualTo(Money.of("10.00"));
    assertThat(Money.of("10")).hasToString("10.00");
  }

  @Test
  void of_moreThanTwoDecimals_isRefused() {
    assertThatThrownBy(() -> Money.of("10.001")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void of_negativeAmount_isRefused() {
    assertThatThrownBy(() -> Money.of("-0.01")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void of_zero_isAccepted() {
    assertThat(Money.of("0")).hasToString("0.00");
  }

  /** BR9 examples from the acceptance criteria, plus the cases where rounding matters. */
  @ParameterizedTest(name = "{0} minus {1}% is {2}")
  @CsvSource({
    "10.00, 10, 9.00",
    "25.00, 10, 22.50",
    // 0.045 rounds up to 0.05; half-even rounding would give 0.04
    "0.05, 10, 0.05",
    // 8.991 rounds down to 8.99
    "9.99, 10, 8.99",
    // 11.385 rounds up to 11.39
    "12.65, 10, 11.39",
    "10.00, 0, 10.00",
    "10.00, 100, 0.00"
  })
  void minusPercent_roundsHalfUpToTheCent(String amount, int percent, String expected) {
    assertThat(Money.of(amount).minusPercent(percent)).isEqualTo(Money.of(expected));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 101})
  void minusPercent_outsideZeroToHundred_isRefused(int percent) {
    assertThatThrownBy(() -> Money.of("10.00").minusPercent(percent))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ------------------------------------------------------------- US06: overtime and settlement

  @ParameterizedTest(name = "{0} x {1} = {2}")
  @CsvSource({"2.00, 0, 0.00", "2.00, 1, 2.00", "2.00, 17, 34.00", "1.50, 3, 4.50"})
  void times_multipliesByAWholeNumber(String amount, long times, String expected) {
    assertThat(Money.of(amount).times(times)).isEqualTo(Money.of(expected));
  }

  @Test
  void times_negative_isRefused() {
    Money price = Money.of("2.00");

    assertThatThrownBy(() -> price.times(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "{0} - {1} = {2}")
  @CsvSource({"30.00, 4.00, 26.00", "30.00, 30.00, 0.00", "30.00, 0.00, 30.00"})
  void minus_subtractsUpToZero(String amount, String taken, String expected) {
    assertThat(Money.of(amount).minus(Money.of(taken))).isEqualTo(Money.of(expected));
  }

  /** Money is never negative: taking more than there is is a bug in the caller. */
  @Test
  void minus_moreThanTheAmount_isRefused() {
    Money deposit = Money.of("30.00");
    Money charge = Money.of("30.01");

    assertThatThrownBy(() -> deposit.minus(charge)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "{0} at most {1} = {2}")
  @CsvSource({"4.00, 30.00, 4.00", "34.00, 30.00, 30.00", "30.00, 30.00, 30.00"})
  void atMost_capsTheAmount(String amount, String cap, String expected) {
    assertThat(Money.of(amount).atMost(Money.of(cap))).isEqualTo(Money.of(expected));
  }

  @Test
  void isZero_onlyForNoMoney() {
    assertThat(Money.ZERO.isZero()).isTrue();
    assertThat(Money.of("0.01").isZero()).isFalse();
  }
}
