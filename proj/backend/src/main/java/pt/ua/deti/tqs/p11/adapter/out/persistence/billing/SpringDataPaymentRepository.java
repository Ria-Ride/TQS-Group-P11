package pt.ua.deti.tqs.p11.adapter.out.persistence.billing;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, UUID> {}
