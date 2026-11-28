package pt.ua.deti.tqs.p11.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/**
 * BR12: overtime is free during the grace, then charged per started block. The first table is the
 * acceptance criteria of US06 (grace 10 min, block 15 min, 2.00 EUR per block), with the second
 * after each boundary, since a started block counts whole.
 */
class OvertimeTest {

  private static final BookingRules RULES =
      new BookingRules(
          Money.of("30.00"),
          Duration.ofMinutes(15),
          Duration.ofMinutes(15),
          Duration.ofMinutes(10),
          Duration.ofMinutes(15),
          Money.of("2.00"));

  @ParameterizedTest(name = "{0} s late: {1} EUR")
  @CsvSource({
    "0,     0.00",
    "600,   0.00", // 10:00, the end of the grace
    "601,   2.00", // 10:01 starts the first block
    "660,   2.00", // 11 min
    "1500,  2.00", // 25 min, the end of the first block
    "1501,  4.00",
    "1560,  4.00", // 26 min
    "2400,  4.00", // 40 min, the end of the second block
    "15060, 34.00" // 251 min: 241 past the grace, 17 started blocks
  })
  void charge_acceptanceCriteriaExamples(long secondsLate, String charge) {
    assertThat(Overtime.charge(RULES, Duration.ofSeconds(secondsLate))).isEqualTo(Money.of(charge));
  }

  /** Ended before the planned end: nothing to charge. */
  @ParameterizedTest(name = "{0} s early")
  @CsvSource({"-1", "-3600"})
  void charge_endedEarly_isZero(long seconds) {
    assertThat(Overtime.charge(RULES, Duration.ofSeconds(seconds))).isEqualTo(Money.ZERO);
  }

  /** BR21: the rules the booking kept are the ones applied, whatever they are. */
  @ParameterizedTest(name = "grace {0} min, block {1} min, {2} EUR, {3} min late: {4} EUR")
  @CsvSource({"0,  30, 1.50, 1,  1.50", "0,  30, 1.50, 31, 3.00", "5,  10, 0.75, 26, 2.25"})
  void charge_appliesTheRulesOfTheBooking(
      long grace, long block, String price, long minutesLate, String charge) {
    BookingRules rules =
        new BookingRules(
            Money.of("30.00"),
            Duration.ofMinutes(15),
            Duration.ofMinutes(15),
            Duration.ofMinutes(grace),
            Duration.ofMinutes(block),
            Money.of(price));

    assertThat(Overtime.charge(rules, Duration.ofMinutes(minutesLate))).isEqualTo(Money.of(charge));
  }
}
