package pt.ua.deti.tqs.p11.adapter.out.persistence.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.booking.Booking;
import pt.ua.deti.tqs.p11.domain.booking.BookingRules;
import pt.ua.deti.tqs.p11.domain.booking.BookingState;
import pt.ua.deti.tqs.p11.domain.booking.BookingTerms;

/** JPA mapping of bookings: the terms, the rules they keep (BR21) and the lifecycle fields. */
@Entity
@Table(name = "bookings")
class BookingJpaEntity {

  @Id private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "vehicle_id", nullable = false)
  private UUID vehicleId;

  @Column(name = "pass_id", nullable = false)
  private UUID passId;

  @Column(name = "start_hub_id", nullable = false)
  private UUID startHubId;

  @Column(name = "planned_start", nullable = false)
  private Instant plannedStart;

  @Column(name = "planned_end", nullable = false)
  private Instant plannedEnd;

  @Column(name = "deposit_reference", nullable = false, length = 100)
  private String depositReference;

  @Column(name = "deposit_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal depositAmount;

  @Column(name = "start_window_seconds", nullable = false)
  private long startWindowSeconds;

  @Column(name = "repositioning_window_seconds", nullable = false)
  private long repositioningWindowSeconds;

  @Column(name = "overtime_grace_seconds", nullable = false)
  private long overtimeGraceSeconds;

  @Column(name = "overtime_block_seconds", nullable = false)
  private long overtimeBlockSeconds;

  @Column(name = "overtime_price_per_block", nullable = false, precision = 10, scale = 2)
  private BigDecimal overtimePricePerBlock;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BookingState state;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "end_hub_id")
  private UUID endHubId;

  @Column(name = "cancel_reason", length = 100)
  private String cancelReason;

  @Column(name = "full_hub_attempt_at")
  private Instant fullHubAttemptAt;

  /** For JPA only. */
  protected BookingJpaEntity() {}

  static BookingJpaEntity from(Booking booking) {
    BookingTerms terms = booking.terms();
    BookingRules rules = booking.rules();
    BookingJpaEntity entity = new BookingJpaEntity();
    entity.id = booking.id();
    entity.accountId = terms.accountId();
    entity.vehicleId = terms.vehicleId();
    entity.passId = terms.passId();
    entity.startHubId = terms.startHubId();
    entity.plannedStart = terms.plannedStart();
    entity.plannedEnd = terms.plannedEnd();
    entity.depositReference = booking.depositReference();
    entity.depositAmount = rules.deposit().amount();
    entity.startWindowSeconds = rules.startWindow().toSeconds();
    entity.repositioningWindowSeconds = rules.repositioningWindow().toSeconds();
    entity.overtimeGraceSeconds = rules.overtimeGrace().toSeconds();
    entity.overtimeBlockSeconds = rules.overtimeBlock().toSeconds();
    entity.overtimePricePerBlock = rules.overtimePricePerBlock().amount();
    entity.state = booking.state();
    entity.startedAt = booking.startedAt();
    entity.endedAt = booking.endedAt();
    entity.endHubId = booking.endHubId();
    entity.cancelReason = booking.cancelReason();
    entity.fullHubAttemptAt = booking.fullHubAttemptAt();
    return entity;
  }

  /** Goes through the domain constructors, so a row that breaks a rule is never used. */
  Booking toDomain() {
    BookingTerms terms =
        new BookingTerms(accountId, vehicleId, passId, startHubId, plannedStart, plannedEnd);
    BookingRules rules =
        new BookingRules(
            new Money(depositAmount),
            Duration.ofSeconds(startWindowSeconds),
            Duration.ofSeconds(repositioningWindowSeconds),
            Duration.ofSeconds(overtimeGraceSeconds),
            Duration.ofSeconds(overtimeBlockSeconds),
            new Money(overtimePricePerBlock));
    return new Booking(
        id,
        terms,
        rules,
        depositReference,
        state,
        startedAt,
        endedAt,
        endHubId,
        cancelReason,
        fullHubAttemptAt);
  }
}
