package pt.ua.deti.tqs.p11.domain.pass;

import java.util.Objects;
import java.util.UUID;

/** A hotel of the client's group whose guests get {@code discountPercent} off a pass (BR9). */
public record PartnerAccommodation(UUID id, String name, int discountPercent) {

  public PartnerAccommodation {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    if (discountPercent < 0 || discountPercent > 100) {
      throw new IllegalArgumentException("A discount is from 0 to 100%: " + discountPercent);
    }
  }
}
