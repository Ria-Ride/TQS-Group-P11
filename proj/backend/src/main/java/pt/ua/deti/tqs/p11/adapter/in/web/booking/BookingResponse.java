package pt.ua.deti.tqs.p11.adapter.in.web.booking;

import java.time.Instant;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.booking.Booking;

/**
 * A booking: what was booked, its state and the deposit held for it (as a string such as "30.00").
 * Times are in UTC. startedAt is null until the booking is started.
 */
record BookingResponse(
    UUID id,
    String state,
    UUID vehicleId,
    UUID passId,
    UUID startHubId,
    Instant plannedStart,
    Instant plannedEnd,
    Instant startedAt,
    String deposit,
    String currency) {

  static BookingResponse from(Booking booking) {
    return new BookingResponse(
        booking.id(),
        booking.state().name(),
        booking.terms().vehicleId(),
        booking.terms().passId(),
        booking.terms().startHubId(),
        booking.terms().plannedStart(),
        booking.terms().plannedEnd(),
        booking.startedAt(),
        booking.rules().deposit().toString(),
        Money.CURRENCY);
  }
}
