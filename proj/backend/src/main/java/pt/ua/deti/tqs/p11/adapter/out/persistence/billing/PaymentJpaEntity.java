package pt.ua.deti.tqs.p11.adapter.out.persistence.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.billing.Payment;
import pt.ua.deti.tqs.p11.domain.billing.PaymentKind;
import pt.ua.deti.tqs.p11.domain.billing.PaymentState;

/** JPA mapping of payments: pass charges (US03) and booking deposits (US04). */
@Entity
@Table(name = "payments")
class PaymentJpaEntity {

  @Id private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentKind kind;

  @Column(name = "pass_id")
  private UUID passId;

  @Column(name = "booking_id")
  private UUID bookingId;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentState state;

  @Column(name = "gateway_reference", length = 100)
  private String gatewayReference;

  @Column(name = "failure_reason", length = 50)
  private String failureReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "captured_amount", precision = 10, scale = 2)
  private BigDecimal capturedAmount;

  /** For JPA only. */
  protected PaymentJpaEntity() {}

  static PaymentJpaEntity from(Payment payment) {
    PaymentJpaEntity entity = new PaymentJpaEntity();
    entity.id = payment.id();
    entity.accountId = payment.accountId();
    entity.passId = payment.passId().orElse(null);
    entity.amount = payment.amount().amount();
    entity.state = payment.state();
    entity.gatewayReference = payment.gatewayReference().orElse(null);
    entity.failureReason = payment.failureReason().orElse(null);
    entity.createdAt = payment.at();
    entity.kind = payment.kind();
    entity.bookingId = payment.bookingId().orElse(null);
    entity.capturedAmount = payment.capturedAmount().map(Money::amount).orElse(null);
    return entity;
  }

  /** US05: read back through the domain constructor, so a row that breaks a rule is never used. */
  Payment toDomain() {
    return new Payment(
        id,
        accountId,
        kind,
        new Money(amount),
        state,
        gatewayReference,
        failureReason,
        passId,
        bookingId,
        createdAt,
        capturedAmount == null ? null : new Money(capturedAmount));
  }
}
