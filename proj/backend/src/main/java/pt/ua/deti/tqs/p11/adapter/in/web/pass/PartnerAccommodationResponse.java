package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.pass.PartnerAccommodation;

/** A partner hotel and the discount its guests get on a pass (BR9). */
record PartnerAccommodationResponse(UUID id, String name, int discountPercent) {

  static PartnerAccommodationResponse from(PartnerAccommodation hotel) {
    return new PartnerAccommodationResponse(hotel.id(), hotel.name(), hotel.discountPercent());
  }
}
