package pt.ua.deti.tqs.p11.domain.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount of money in euros, with cents. Always kept with two decimals, so 10 and 10.00 are the
 * same amount and print the same way. Never negative: refunds and charges are separate payments.
 * BigDecimal, not double: 0.1 + 0.2 is exactly 0.3 here.
 */
public record Money(BigDecimal amount) {

  /** The platform charges in one currency only. */
  public static final String CURRENCY = "EUR";

  private static final int CENTS = 2;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  public Money {
    Objects.requireNonNull(amount, "amount");
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("An amount cannot be negative: " + amount);
    }
    if (amount.stripTrailingZeros().scale() > CENTS) {
      throw new IllegalArgumentException("An amount has at most two decimals: " + amount);
    }
    amount = amount.setScale(CENTS, RoundingMode.UNNECESSARY);
  }

  /** Parses an amount such as "10", "9.5" or "22.50". */
  public static Money of(String amount) {
    return new Money(new BigDecimal(amount));
  }

  /**
   * BR9: the amount after a discount of {@code percent}, rounded half-up to the cent (0.045 becomes
   * 0.05).
   *
   * @throws IllegalArgumentException if percent is not from 0 to 100
   */
  public Money minusPercent(int percent) {
    if (percent < 0 || percent > 100) {
      throw new IllegalArgumentException("A discount is from 0 to 100%: " + percent);
    }
    BigDecimal kept = HUNDRED.subtract(BigDecimal.valueOf(percent));
    return new Money(amount.multiply(kept).divide(HUNDRED, CENTS, RoundingMode.HALF_UP));
  }

  /** The amount with two decimals, as clients show it ("9.00"). */
  @Override
  public String toString() {
    return amount.toPlainString();
  }
}
