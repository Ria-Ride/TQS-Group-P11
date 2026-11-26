package pt.ua.deti.tqs.p11.adapter.out.persistence.booking;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.ua.deti.tqs.p11.domain.booking.BookingState;

interface SpringDataBookingRepository extends JpaRepository<BookingJpaEntity, UUID> {

  List<BookingJpaEntity> findByAccountIdAndStateInOrderByPlannedStartAsc(
      UUID accountId, Collection<BookingState> states);
}
