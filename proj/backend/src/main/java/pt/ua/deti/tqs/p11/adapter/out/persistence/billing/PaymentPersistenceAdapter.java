package pt.ua.deti.tqs.p11.adapter.out.persistence.billing;

import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.billing.PaymentRepository;
import pt.ua.deti.tqs.p11.domain.billing.Payment;

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
}
