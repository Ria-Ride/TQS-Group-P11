package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import java.time.LocalDate;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.pass.Stay;

/**
 * A stay code: its hotel and discount, the days it is valid, and whether a tourist has linked it to
 * an account. Who redeemed it is not shown: the client only needs to know it was used.
 */
record StayCodeResponse(
    UUID id,
    String code,
    UUID accommodationId,
    String accommodationName,
    int discountPercent,
    LocalDate checkIn,
    LocalDate checkOut,
    boolean redeemed) {

  static StayCodeResponse from(Stay stay) {
    return new StayCodeResponse(
        stay.id(),
        stay.code(),
        stay.accommodation().id(),
        stay.accommodation().name(),
        stay.discountPercent(),
        stay.checkIn(),
        stay.checkOut(),
        stay.redeemedBy().isPresent());
  }
}
