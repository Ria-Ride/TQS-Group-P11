package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import java.util.List;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.pass.PassType;

/** A pass type as clients see it. The price is a string ("10.00"), never a floating point. */
record PassTypeResponse(
    UUID id,
    String code,
    String name,
    String price,
    String currency,
    int validityDays,
    List<VehicleType> vehicleTypes) {

  static PassTypeResponse from(PassType type) {
    return new PassTypeResponse(
        type.id(),
        type.code(),
        type.name(),
        type.basePrice().toString(),
        Money.CURRENCY,
        type.validityDays(),
        type.vehicleTypes().stream().sorted().toList());
  }
}
