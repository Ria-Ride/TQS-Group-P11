package pt.ua.deti.tqs.p11.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import pt.ua.deti.tqs.p11.adapter.in.scheduling.ReservationExpiryJob;
import pt.ua.deti.tqs.p11.application.booking.ManageBookingUseCase;

/**
 * BR7: the scheduled expiry of reservations. On by default; app.bookings.expiry.enabled=false turns
 * it off, as the acceptance tests do so that only their steps decide when it runs.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "app.bookings.expiry.enabled",
    havingValue = "true",
    matchIfMissing = true)
class BookingExpiryConfiguration {

  @Bean
  ReservationExpiryJob reservationExpiryJob(ManageBookingUseCase bookings) {
    return new ReservationExpiryJob(bookings);
  }
}
