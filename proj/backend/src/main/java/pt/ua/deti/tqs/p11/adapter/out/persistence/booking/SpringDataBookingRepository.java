package pt.ua.deti.tqs.p11.adapter.out.persistence.booking;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBookingRepository extends JpaRepository<BookingJpaEntity, UUID> {}
