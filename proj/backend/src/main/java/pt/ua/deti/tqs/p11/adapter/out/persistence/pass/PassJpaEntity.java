package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.pass.Pass;

/**
 * JPA mapping of passes. The pass type code is not a column: it is read through pass_type_id, so it
 * cannot disagree with the type.
 */
@Entity
@Table(name = "passes")
class PassJpaEntity {

  @Id private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "pass_type_id", nullable = false)
  private UUID passTypeId;

  @Column(name = "stay_id")
  private UUID stayId;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal price;

  @Column(name = "valid_from", nullable = false)
  private Instant validFrom;

  @Column(name = "valid_to", nullable = false)
  private Instant validTo;

  /** Read only: pass_type_id above is the column that is written. */
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "pass_type_id", insertable = false, updatable = false)
  private PassTypeJpaEntity passType;

  /** For JPA only. */
  protected PassJpaEntity() {}

  static PassJpaEntity from(Pass pass) {
    PassJpaEntity entity = new PassJpaEntity();
    entity.id = pass.id();
    entity.accountId = pass.accountId();
    entity.passTypeId = pass.passTypeId();
    entity.stayId = pass.stayId().orElse(null);
    entity.price = pass.price().amount();
    entity.validFrom = pass.validFrom();
    entity.validTo = pass.validTo();
    return entity;
  }

  Pass toDomain() {
    return new Pass(
        id,
        accountId,
        passTypeId,
        passType.toDomain().code(),
        stayId,
        new Money(price),
        validFrom,
        validTo);
  }
}
