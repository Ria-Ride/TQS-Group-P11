package pt.ua.deti.tqs.p11.application.billing;

import pt.ua.deti.tqs.p11.domain.billing.Payment;

/** Port: where payments are kept, approved or not, so every charge can be traced. */
public interface PaymentRepository {

  void save(Payment payment);
}
