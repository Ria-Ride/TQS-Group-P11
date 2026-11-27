package pt.ua.deti.tqs.p11.adapter.in.web.rules;

import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.rules.RulesOfUse;

/** What the current rules say about one vehicle type. The deposit is a string ("20.00"). */
record VehicleTypeResponse(
    VehicleType type, String depositAmount, String currency, int batteryPercentPer30Minutes) {

  static VehicleTypeResponse of(RulesOfUse rules, VehicleType type) {
    return new VehicleTypeResponse(
        type,
        rules.deposit(type).toString(),
        Money.CURRENCY,
        rules.vehicleTypes().get(type).batteryPercentPer30Minutes());
  }
}
