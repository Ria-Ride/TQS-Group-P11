package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.pass.Stay;

/** JPA mapping of stays. After creation (US14), only redeemed_by changes (US03). */
@Entity
@Table(name = "stays")
class StayJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 32)
  private String code;

  // The discount is needed with every stay that is read.
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "accommodation_id", nullable = false)
  private PartnerAccommodationJpaEntity accommodation;

  @Column(name = "check_in", nullable = false)
  private LocalDate checkIn;

  @Column(name = "check_out", nullable = false)
  private LocalDate checkOut;

  @Column(name = "redeemed_by")
  private UUID redeemedBy;

  /** For JPA only. */
  protected StayJpaEntity() {}

  static StayJpaEntity from(Stay stay, PartnerAccommodationJpaEntity accommodation) {
    StayJpaEntity entity = new StayJpaEntity();
    entity.id = stay.id();
    entity.code = stay.code().toUpperCase(Locale.ROOT);
    entity.accommodation = accommodation;
    entity.checkIn = stay.checkIn();
    entity.checkOut = stay.checkOut();
    entity.redeemedBy = stay.redeemedBy().orElse(null);
    return entity;
  }

  Stay toDomain() {
    return new Stay(id, code, accommodation.toDomain(), checkIn, checkOut, redeemedBy);
  }

  /** The account the code is linked to, or null to free it. */
  void linkTo(UUID account) {
    redeemedBy = account;
  }
}
