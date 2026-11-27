package pt.ua.deti.tqs.p11.adapter.in.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import pt.ua.deti.tqs.p11.application.booking.ManageBookingUseCase;

/**
 * BR7: expires the reservations nobody started in their window, every app.bookings.expiry.interval
 * (30 seconds by default). Only a trigger: the rule and its safety against running twice are in
 * ManageBookingUseCase. A reservation past its window cannot be started even before this runs
 * (Booking.start), so the interval only decides how soon the vehicle and the deposit are given
 * back.
 */
public class ReservationExpiryJob {

  private static final Logger LOG = LoggerFactory.getLogger(ReservationExpiryJob.class);

  private final ManageBookingUseCase bookings;

  public ReservationExpiryJob(ManageBookingUseCase bookings) {
    this.bookings = bookings;
  }

  @Scheduled(
      fixedDelayString = "${app.bookings.expiry.interval:PT30S}",
      initialDelayString = "${app.bookings.expiry.interval:PT30S}")
  public void expireOverdueReservations() {
    int expired = bookings.expireOverdue();
    if (expired > 0) {
      LOG.info("Expired {} reservations not started in their window", expired);
    }
  }
}
