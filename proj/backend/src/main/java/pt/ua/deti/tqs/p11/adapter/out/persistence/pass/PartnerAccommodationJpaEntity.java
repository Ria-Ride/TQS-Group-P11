package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.pass.PartnerAccommodation;

/** JPA mapping of partner_accommodations. Read only until US14. */
@Entity
@Table(name = "partner_accommodations")
class PartnerAccommodationJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 100)
  private String name;

  @Column(name = "discount_percent", nullable = false)
  private int discountPercent;

  /** For JPA only. */
  protected PartnerAccommodationJpaEntity() {}

  PartnerAccommodation toDomain() {
    return new PartnerAccommodation(id, name, discountPercent);
  }
}
