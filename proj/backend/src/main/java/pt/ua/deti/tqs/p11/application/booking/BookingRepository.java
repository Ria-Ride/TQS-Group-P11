package pt.ua.deti.tqs.p11.application.booking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.booking.Booking;

/** Port: where bookings are kept, with the rules each one keeps (BR21). */
public interface BookingRepository {

  /** Inserts the booking, or updates it when one with the same id exists (every transition). */
  Booking save(Booking booking);

  Optional<Booking> findById(UUID id);

  /** BR6: the account's Reserved and Active bookings, earliest planned start first. */
  List<Booking> findOngoingByAccount(UUID accountId);
}
