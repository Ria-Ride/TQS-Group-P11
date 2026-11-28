package pt.ua.deti.tqs.p11.domain.booking;

import java.util.Objects;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/**
 * BR13: how the deposit of an ended ride is settled. The charge is taken from the deposit, never
 * more than the deposit, and the rest is given back: with a 30.00 EUR deposit, a 4.00 charge
 * captures 4.00 and releases 26.00; a 34.00 charge captures 30.00 and releases nothing.
 *
 * @param charge the overtime charge (BR12), which may be more than the deposit
 * @param captured what is taken from the deposit: the charge, at most the deposit
 * @param released what is given back: the deposit minus what is taken
 */
public record Settlement(Money charge, Money captured, Money released) {

  public Settlement {
    Objects.requireNonNull(charge, "charge");
    Objects.requireNonNull(captured, "captured");
    Objects.requireNonNull(released, "released");
  }

  public static Settlement of(Money charge, Money deposit) {
    Objects.requireNonNull(charge, "charge");
    Objects.requireNonNull(deposit, "deposit");
    Money captured = charge.atMost(deposit);
    return new Settlement(charge, captured, deposit.minus(captured));
  }
}
