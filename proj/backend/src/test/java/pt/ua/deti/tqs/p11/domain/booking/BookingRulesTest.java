package pt.ua.deti.tqs.p11.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/**
 * BR21: the rules of use a booking keeps from the moment it is created, so later changes by the
 * client do not affect it.
 */
class BookingRulesTest {

  private static final Money DEPOSIT = Money.of("30.00");
  private static final Money PRICE = Money.of("2.00");
  private static final Duration WINDOW = Duration.ofMinutes(15);
  private static final Duration GRACE = Duration.ofMinutes(10);
  private static final Duration BLOCK = Duration.ofMinutes(15);

  @Test
  void new_keepsEveryValue() {
    BookingRules rules = new BookingRules(DEPOSIT, WINDOW, GRACE, BLOCK, PRICE);

    assertThat(rules.deposit()).isEqualTo(DEPOSIT);
    assertThat(rules.startWindow()).isEqualTo(WINDOW);
    assertThat(rules.overtimeGrace()).isEqualTo(GRACE);
    assertThat(rules.overtimeBlock()).isEqualTo(BLOCK);
    assertThat(rules.overtimePricePerBlock()).isEqualTo(PRICE);
  }

  @Test
  void new_noGraceAndNoStartWindow_isAccepted() {
    BookingRules rules = new BookingRules(DEPOSIT, Duration.ZERO, Duration.ZERO, BLOCK, PRICE);

    assertThat(rules.overtimeGrace()).isZero();
    assertThat(rules.startWindow()).isZero();
  }

  @Test
  void new_negativeStartWindow_isRefused() {
    assertThatThrownBy(
            () -> new BookingRules(DEPOSIT, Duration.ofMinutes(-1), GRACE, BLOCK, PRICE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_negativeGrace_isRefused() {
    assertThatThrownBy(
            () -> new BookingRules(DEPOSIT, WINDOW, Duration.ofMinutes(-1), BLOCK, PRICE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** A block of zero minutes would make the overtime charge divide by zero (US06). */
  @Test
  void new_zeroBlock_isRefused() {
    assertThatThrownBy(() -> new BookingRules(DEPOSIT, WINDOW, GRACE, Duration.ZERO, PRICE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_withoutADeposit_isRefused() {
    assertThatThrownBy(() -> new BookingRules(null, WINDOW, GRACE, BLOCK, PRICE))
        .isInstanceOf(NullPointerException.class);
  }
}