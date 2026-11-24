package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import java.time.Instant;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.pass.Pass;

/**
 * An issued pass: what was paid (after any stay discount, as a string such as "9.00") and when it
 * is valid, in UTC (BR8).
 */
record PassResponse(
    UUID id,
    UUID passTypeId,
    String passTypeCode,
    String price,
    String currency,
    Instant validFrom,
    Instant validTo) {

  static PassResponse from(Pass pass) {
    return new PassResponse(
        pass.id(),
        pass.passTypeId(),
        pass.passTypeCode(),
        pass.price().toString(),
        Money.CURRENCY,
        pass.validFrom(),
        pass.validTo());
  }
}
