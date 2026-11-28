package pt.ua.deti.tqs.p11.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.rules.RulesOfUse;
import pt.ua.deti.tqs.p11.support.TestRules;

/**
 * BR21: the rules of use a booking keeps from the moment it is created, so later changes by the
 * client do not affect it.
 */
class BookingRulesTest {

  private static final Money DEPOSIT = Money.of("30.00");
  private static final Money PRICE = Money.of("2.00");
  private static final Duration WINDOW = Duration.ofMinutes(15);
  private static final Duration REPOSITIONING = Duration.ofMinutes(15);
  private static final Duration GRACE = Duration.ofMinutes(10);
  private static final Duration BLOCK = Duration.ofMinutes(15);

  @Test
  void new_keepsEveryValue() {
    BookingRules rules = new BookingRules(DEPOSIT, WINDOW, REPOSITIONING, GRACE, BLOCK, PRICE);

    assertThat(rules.deposit()).isEqualTo(DEPOSIT);
    assertThat(rules.startWindow()).isEqualTo(WINDOW);
    assertThat(rules.repositioningWindow()).isEqualTo(REPOSITIONING);
    assertThat(rules.overtimeGrace()).isEqualTo(GRACE);
    assertThat(rules.overtimeBlock()).isEqualTo(BLOCK);
    assertThat(rules.overtimePricePerBlock()).isEqualTo(PRICE);
  }

  /** BR21: a new booking keeps its type's deposit and the shared rules of the version in force. */
  @ParameterizedTest(name = "{0}: deposit {1}")
  @CsvSource({"E_BIKE, 30.00", "E_SCOOTER, 50.00"})
  void of_keepsTheDepositOfTheTypeAndTheSharedRules(VehicleType type, String deposit) {
    RulesOfUse inForce = TestRules.bookingRules();

    assertThat(BookingRules.of(inForce, type))
        .isEqualTo(new BookingRules(Money.of(deposit), WINDOW, REPOSITIONING, GRACE, BLOCK, PRICE));
  }

  @Test
  void new_noGraceAndNoStartWindow_isAccepted() {
    BookingRules rules =
        new BookingRules(DEPOSIT, Duration.ZERO, REPOSITIONING, Duration.ZERO, BLOCK, PRICE);

    assertThat(rules.overtimeGrace()).isZero();
    assertThat(rules.startWindow()).isZero();
  }

  @Test
  void new_negativeStartWindow_isRefused() {
    assertThatThrownBy(() ->
            new BookingRules(DEPOSIT, Duration.ofMinutes(-1), REPOSITIONING, GRACE, BLOCK, PRICE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_negativeRepositioningWindow_isRefused() {
    assertThatThrownBy(
            () -> new BookingRules(DEPOSIT, WINDOW, Duration.ofMinutes(-1), GRACE, BLOCK, PRICE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_negativeGrace_isRefused() {
    assertThatThrownBy(() ->
            new BookingRules(DEPOSIT, WINDOW, REPOSITIONING, Duration.ofMinutes(-1), BLOCK, PRICE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** A block of zero minutes would make the overtime charge divide by zero (US06). */
  @Test
  void new_zeroBlock_isRefused() {
    assertThatThrownBy(
            () -> new BookingRules(DEPOSIT, WINDOW, REPOSITIONING, GRACE, Duration.ZERO, PRICE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_withoutADeposit_isRefused() {
    assertThatThrownBy(() -> new BookingRules(null, WINDOW, REPOSITIONING, GRACE, BLOCK, PRICE))
        .isInstanceOf(NullPointerException.class);
  }
}
