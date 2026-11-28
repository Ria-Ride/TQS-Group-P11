package pt.ua.deti.tqs.p11.application.billing;

import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Payment;

/** Port: where payments are kept, approved or not, so every charge can be traced. */
public interface PaymentRepository {

  void save(Payment payment);

  /** BR11: the deposit held for a booking, whatever its state now. */
  Optional<Payment> findDepositOfBooking(UUID bookingId);
}
