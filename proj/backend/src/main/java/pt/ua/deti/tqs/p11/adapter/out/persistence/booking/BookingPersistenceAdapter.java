package pt.ua.deti.tqs.p11.adapter.out.persistence.booking;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.booking.BookingRepository;
import pt.ua.deti.tqs.p11.domain.booking.Booking;

/**
 * BookingRepository backed by PostgreSQL through Spring Data JPA. Saves are flushed at once, so a
 * broken database rule (such as a second ongoing booking of a vehicle, BR3) fails the save itself.
 */
@Component
class BookingPersistenceAdapter implements BookingRepository {

  private final SpringDataBookingRepository repository;

  BookingPersistenceAdapter(SpringDataBookingRepository repository) {
    this.repository = repository;
  }

  @Override
  public Booking save(Booking booking) {
    return repository.saveAndFlush(BookingJpaEntity.from(booking)).toDomain();
  }

  @Override
  public Optional<Booking> findById(UUID id) {
    return repository.findById(id).map(BookingJpaEntity::toDomain);
  }
}
