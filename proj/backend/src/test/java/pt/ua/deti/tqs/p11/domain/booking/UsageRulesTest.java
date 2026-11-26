package pt.ua.deti.tqs.p11.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * The client's current rules of use (seeded until US14) and the copy a new booking keeps of them
 * (BR21).
 */
class UsageRulesTest {

  private static final Map<VehicleType, Money> DEPOSITS =
      Map.of(VehicleType.E_BIKE, Money.of("30.00"), VehicleType.E_SCOOTER, Money.of("50.00"));
  private static final Duration WINDOW = Duration.ofMinutes(15);
  private static final Duration GRACE = Duration.ofMinutes(10);
  private static final Duration BLOCK = Duration.ofMinutes(15);
  private static final Money PRICE = Money.of("2.00");

  private static UsageRules rules(Duration maxDuration) {
    return new UsageRules(maxDuration, DEPOSITS, WINDOW, GRACE, BLOCK, PRICE);
  }

  /** BR11: the deposit is the one of the vehicle's type; the rest is the same for every type. */
  @Test
  void forType_keepsTheDepositOfTheTypeAndTheSharedRules() {
    UsageRules rules = rules(Duration.ofHours(4));

    assertThat(rules.forType(VehicleType.E_BIKE))
        .isEqualTo(new BookingRules(Money.of("30.00"), WINDOW, GRACE, BLOCK, PRICE));
    assertThat(rules.forType(VehicleType.E_SCOOTER))
        .isEqualTo(new BookingRules(Money.of("50.00"), WINDOW, GRACE, BLOCK, PRICE));
  }

  @Test
  void new_maxDurationOfOneDay_isAccepted() {
    assertThat(rules(Duration.ofHours(24)).maxBookingDuration()).isEqualTo(Duration.ofHours(24));
  }

  /** US14: maxBookingDuration is above 0 and at most 24 hours. */
  @Test
  void new_maxDurationAboveOneDay_isRefused() {
    Duration tooLong = Duration.ofHours(24).plusSeconds(1);

    assertThatThrownBy(() -> rules(tooLong)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_zeroMaxDuration_isRefused() {
    assertThatThrownBy(() -> rules(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_withoutADepositForEveryType_isRefused() {
    Map<VehicleType, Money> onlyBikes = Map.of(VehicleType.E_BIKE, Money.of("30.00"));
    Duration max = Duration.ofHours(4);

    assertThatThrownBy(() -> new UsageRules(max, onlyBikes, WINDOW, GRACE, BLOCK, PRICE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** The rules a booking keeps are checked when the client's rules are set, not later. */
  @Test
  void new_zeroOvertimeBlock_isRefused() {
    Duration max = Duration.ofHours(4);

    assertThatThrownBy(
            () -> new UsageRules(max, DEPOSITS, WINDOW, GRACE, Duration.ZERO, PRICE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}