package pt.ua.deti.tqs.p11.adapter.out.persistence.billing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.billing.PaymentRepository;
import pt.ua.deti.tqs.p11.domain.billing.Payment;
import pt.ua.deti.tqs.p11.domain.billing.PaymentKind;

/** PaymentRepository backed by PostgreSQL through Spring Data JPA. */
@Component
class PaymentPersistenceAdapter implements PaymentRepository {

  private final SpringDataPaymentRepository repository;

  PaymentPersistenceAdapter(SpringDataPaymentRepository repository) {
    this.repository = repository;
  }

  @Override
  public void save(Payment payment) {
    repository.saveAndFlush(PaymentJpaEntity.from(payment));
  }

  @Override
  public Optional<Payment> findDepositOfBooking(UUID bookingId) {
    return repository
        .findByBookingIdAndKind(bookingId, PaymentKind.DEPOSIT)
        .map(PaymentJpaEntity::toDomain);
  }
}
