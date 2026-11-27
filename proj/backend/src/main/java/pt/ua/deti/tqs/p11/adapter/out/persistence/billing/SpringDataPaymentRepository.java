package pt.ua.deti.tqs.p11.adapter.out.persistence.billing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.ua.deti.tqs.p11.domain.billing.PaymentKind;

interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, UUID> {

  /** One deposit per booking (uk_payments_booking). */
  Optional<PaymentJpaEntity> findByBookingIdAndKind(UUID bookingId, PaymentKind kind);
}
