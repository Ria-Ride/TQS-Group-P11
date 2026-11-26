package pt.ua.deti.tqs.p11.adapter.out.persistence.booking;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.booking.BookingRepository;
import pt.ua.deti.tqs.p11.domain.booking.Booking;
import pt.ua.deti.tqs.p11.domain.booking.BookingRefusal;
import pt.ua.deti.tqs.p11.domain.booking.BookingRefusedException;
import pt.ua.deti.tqs.p11.domain.booking.BookingState;

/**
 * BookingRepository backed by PostgreSQL through Spring Data JPA. Saves are flushed at once, so a
 * broken database rule fails the save itself; a second ongoing booking of a vehicle (BR3) is
 * refused as the business rule it is, the same refusal a tourist gets for a taken vehicle.
 */
@Component
class BookingPersistenceAdapter implements BookingRepository {

  private final SpringDataBookingRepository repository;

  static final String ONGOING_VEHICLE_INDEX = "uk_bookings_ongoing_vehicle";

  /** The states that hold a vehicle and a period, as BookingState says. */
  private static final List<BookingState> ONGOING =
      Arrays.stream(BookingState.values()).filter(BookingState::isOngoing).toList();

  BookingPersistenceAdapter(SpringDataBookingRepository repository) {
    this.repository = repository;
  }

  /**
   * @throws BookingRefusedException with VEHICLE_NOT_AVAILABLE if the vehicle already has a
   *     Reserved or Active booking (BR3): the safety net under the use case's vehicle lock
   */
  @Override
  public Booking save(Booking booking) {
    try {
      return repository.saveAndFlush(BookingJpaEntity.from(booking)).toDomain();
    } catch (DataIntegrityViolationException e) {
      if (isOngoingVehicleConflict(e)) {
        throw new BookingRefusedException(BookingRefusal.VEHICLE_NOT_AVAILABLE);
      }
      throw e;
    }
  }

  private static boolean isOngoingVehicleConflict(DataIntegrityViolationException e) {
    String cause = e.getMostSpecificCause().getMessage();
    return cause != null && cause.contains(ONGOING_VEHICLE_INDEX);
  }

  @Override
  public Optional<Booking> findById(UUID id) {
    return repository.findById(id).map(BookingJpaEntity::toDomain);
  }

  @Override
  public List<Booking> findOngoingByAccount(UUID accountId) {
    return repository.findByAccountIdAndStateInOrderByPlannedStartAsc(accountId, ONGOING).stream()
        .map(BookingJpaEntity::toDomain)
        .toList();
  }
}
