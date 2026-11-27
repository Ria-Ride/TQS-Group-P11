package pt.ua.deti.tqs.p11.adapter.in.web.booking;

import java.time.Instant;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.booking.Booking;
import pt.ua.deti.tqs.p11.domain.booking.BookingState;

/**
 * A booking: what was booked, its state and the deposit held for it (as a string such as "30.00").
 * Times are in UTC. startedAt is null until the booking is started; startWindowEndsAt is the last
 * moment a Reserved booking can be started (BR7), null in any other state; cancelReason is set only
 * when Cancelled.
 */
record BookingResponse(
    UUID id,
    String state,
    UUID vehicleId,
    UUID passId,
    UUID startHubId,
    Instant plannedStart,
    Instant plannedEnd,
    Instant startWindowEndsAt,
    Instant startedAt,
    String cancelReason,
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
        booking.state() == BookingState.RESERVED ? booking.startWindowEnd() : null,
        booking.startedAt(),
        booking.cancelReason(),
        booking.rules().deposit().toString(),
        Money.CURRENCY);
  }
}
