package pt.ua.deti.tqs.p11.domain.rules;

import java.util.Objects;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/**
 * What the rules of use say about one type of vehicle: the deposit held for a booking and the
 * percent of battery that every started 30 minutes of riding costs (BR1).
 */
public record VehicleTypeRules(Money deposit, int batteryPercentPer30Minutes) {

  private static final int MAX_PERCENT = 100;

  public VehicleTypeRules {
    Objects.requireNonNull(deposit, "deposit");
    if (batteryPercentPer30Minutes < 0 || batteryPercentPer30Minutes > MAX_PERCENT) {
      throw new IllegalArgumentException(
          "The battery used per 30 minutes is from 0 to 100%: " + batteryPercentPer30Minutes);
    }
  }
}
