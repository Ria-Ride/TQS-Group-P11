package pt.ua.deti.tqs.p11.domain.booking;

import java.time.Duration;
import java.util.Objects;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.rules.RulesOfUse;

/**
 * BR21: the rules of use a booking keeps from the moment it is created, so a later change by the
 * client applies only to new bookings. The deposit is the one of the vehicle's type (BR11); the
 * start window bounds the start of a reservation (BR7); grace, block and price per block give the
 * overtime charge (BR12).
 */
public record BookingRules(
    Money deposit,
    Duration startWindow,
    Duration overtimeGrace,
    Duration overtimeBlock,
    Money overtimePricePerBlock) {

  public BookingRules {
    Objects.requireNonNull(deposit, "deposit");
    Objects.requireNonNull(startWindow, "startWindow");
    Objects.requireNonNull(overtimeGrace, "overtimeGrace");
    Objects.requireNonNull(overtimeBlock, "overtimeBlock");
    Objects.requireNonNull(overtimePricePerBlock, "overtimePricePerBlock");
    if (startWindow.isNegative()) {
      throw new IllegalArgumentException("The start window cannot be negative: " + startWindow);
    }
    if (overtimeGrace.isNegative()) {
      throw new IllegalArgumentException("The overtime grace cannot be negative: " + overtimeGrace);
    }
    if (overtimeBlock.isNegative() || overtimeBlock.isZero()) {
      throw new IllegalArgumentException("The overtime block must be positive: " + overtimeBlock);
    }
  }

  /** What a new booking of this vehicle type keeps of the rules of use in force (US14). */
  public static BookingRules of(RulesOfUse inForce, VehicleType type) {
    return new BookingRules(
        inForce.deposit(type),
        inForce.startWindow(),
        inForce.overtimeGrace(),
        inForce.overtimeBlock(),
        inForce.overtimeRatePerBlock());
  }
}
