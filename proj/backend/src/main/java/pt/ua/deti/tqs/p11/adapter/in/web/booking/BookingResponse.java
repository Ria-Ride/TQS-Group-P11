package pt.ua.deti.tqs.p11.adapter.in.web.booking;

import java.time.Instant;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.booking.Booking;
import pt.ua.deti.tqs.p11.domain.booking.BookingState;
import pt.ua.deti.tqs.p11.domain.booking.Settlement;

/**
 * A booking: what was booked, its state and the deposit held for it. Amounts are strings with cents
 * ("30.00"); times are in UTC.
 *
 * <ul>
 *   <li>startWindowEndsAt: the last moment a Reserved booking can be started (BR7); null otherwise.
 *   <li>startedAt: null until the booking is started.
 *   <li>fullHubAttemptAt: the first refused end at a full hub (BR15), if any.
 *   <li>endedAt, endHubId, overtimeCharge, depositCaptured, depositReleased: only once Completed
 *       (US06). The charge is the overtime (BR12); captured is what the deposit pays of it, at most
 *       the deposit, and released is the rest of the deposit (BR13).
 *   <li>cancelReason: only when Cancelled.
 * </ul>
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
    Instant fullHubAttemptAt,
    Instant endedAt,
    UUID endHubId,
    String overtimeCharge,
    String depositCaptured,
    String depositReleased,
    String cancelReason,
    String deposit,
    String currency) {

  static BookingResponse from(Booking booking) {
    Settlement settlement = booking.settlement().orElse(null);
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
        booking.fullHubAttemptAt(),
        booking.endedAt(),
        booking.endHubId(),
        settlement == null ? null : settlement.charge().toString(),
        settlement == null ? null : settlement.captured().toString(),
        settlement == null ? null : settlement.released().toString(),
        booking.cancelReason(),
        booking.rules().deposit().toString(),
        Money.CURRENCY);
  }
}
