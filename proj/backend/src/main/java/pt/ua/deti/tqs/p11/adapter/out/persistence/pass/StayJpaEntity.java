package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.pass.Stay;

/** JPA mapping of stays. Only redeemed_by changes here; stays are created by US14. */
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

  Stay toDomain() {
    return new Stay(id, code, accommodation.toDomain(), checkIn, checkOut, redeemedBy);
  }

  /** The account the code is linked to, or null to free it. */
  void linkTo(UUID account) {
    redeemedBy = account;
  }
}
