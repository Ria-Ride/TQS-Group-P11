package pt.ua.deti.tqs.p11.adapter.out.persistence.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.ua.deti.tqs.p11.domain.booking.BookingState;

interface SpringDataBookingRepository extends JpaRepository<BookingJpaEntity, UUID> {

  List<BookingJpaEntity> findByAccountIdAndStateInOrderByPlannedStartAsc(
      UUID accountId, Collection<BookingState> states);

  List<BookingJpaEntity> findByStateAndPlannedStartBefore(BookingState state, Instant at);

  Optional<BookingJpaEntity> findByVehicleIdAndState(UUID vehicleId, BookingState state);

  List<BookingJpaEntity> findByAccountIdOrderByPlannedStartDesc(UUID accountId);
}
