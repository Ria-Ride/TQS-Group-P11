package pt.ua.deti.tqs.p11.domain.booking;

import java.time.Duration;
import java.util.Objects;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/**
 * BR12: the overtime charge of a ride. Nothing during the grace; after it, the price of every
 * started block, so one second into a block pays the whole block. With a grace of 10 minutes,
 * blocks of 15 minutes and 2.00 EUR per block: 10 minutes late is free, 11 and 25 minutes cost
 * 2.00, 26 and 40 minutes cost 4.00. Pure arithmetic, to the second, on the rules the booking kept
 * (BR21).
 */
public final class Overtime {

  private Overtime() {}

  /**
   * @param late how long after the planned end the ride is charged until; zero or negative when it
   *     ended in time
   */
  public static Money charge(BookingRules rules, Duration late) {
    Objects.requireNonNull(rules, "rules");
    Objects.requireNonNull(late, "late");
    Duration charged = late.minus(rules.overtimeGrace());
    if (charged.isNegative() || charged.isZero()) {
      return Money.ZERO;
    }
    long blockSeconds = rules.overtimeBlock().toSeconds();
    long startedBlocks = Math.ceilDiv(charged.toSeconds(), blockSeconds);
    return rules.overtimePricePerBlock().times(startedBlocks);
  }
}
