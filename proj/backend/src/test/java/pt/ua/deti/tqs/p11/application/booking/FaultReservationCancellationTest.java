package pt.ua.deti.tqs.p11.application.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.application.billing.DepositResult;
import pt.ua.deti.tqs.p11.application.billing.FakePaymentAdapter;
import pt.ua.deti.tqs.p11.application.billing.PaymentRepository;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.billing.Payment;
import pt.ua.deti.tqs.p11.domain.booking.Booking;
import pt.ua.deti.tqs.p11.domain.booking.BookingRules;
import pt.ua.deti.tqs.p11.domain.booking.BookingState;
import pt.ua.deti.tqs.p11.domain.booking.BookingTerms;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.support.TestRules;

/**
 * BR18: a fault of a Reserved vehicle cancels its reservation and gives the deposit back. Called by
 * maintenance with the vehicle already locked and in Maintenance, so only the booking and the
 * deposit change here (the vehicle has no port in this class at all).
 */
@ExtendWith(MockitoExtension.class)
class FaultReservationCancellationTest {

  private static final Instant NOW = Instant.parse("2026-11-26T10:30:00Z");
  private static final UUID ANA = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000001");
  private static final UUID BIKE_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000001");
  private static final UUID BOOKING_ID = UUID.fromString("6f708192-0000-4000-8000-000000000001");
  private static final UUID PAYMENT_ID = UUID.fromString("5e6f7081-0000-4000-8000-000000000001");
  private static final Money DEPOSIT = Money.of("30.00");

  @Mock private BookingRepository bookings;
  @Mock private PaymentRepository payments;

  private final FakePaymentAdapter gateway = new FakePaymentAdapter();
  private FaultReservationCancellation cancellation;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    cancellation =
        new FaultReservationCancellation(bookings, new DepositReturn(payments, gateway, clock));
  }

  private static Booking reserved() {
    Instant start = NOW.plus(Duration.ofHours(1));
    return Booking.reserve(
        BOOKING_ID,
        new BookingTerms(
            ANA, BIKE_ID, UUID.randomUUID(), UUID.randomUUID(), start, start.plusSeconds(3600)),
        BookingRules.of(TestRules.bookingRules(), VehicleType.E_BIKE),
        "au_1");
  }

  @Test
  void cancelReservedBookingOf_cancelsWithReasonVehicleFaultAndReleasesTheDeposit() {
    when(bookings.findReservedOfVehicle(BIKE_ID)).thenReturn(Optional.of(reserved()));
    when(bookings.save(any())).then(returnsFirstArg());
    when(payments.findDepositOfBooking(BOOKING_ID))
        .thenReturn(
            Optional.of(
                Payment.depositAuthorized(PAYMENT_ID, ANA, DEPOSIT, "au_1", BOOKING_ID, NOW)));

    cancellation.cancelReservedBookingOf(BIKE_ID);

    verify(bookings)
        .save(
            argThat(saved ->
                saved.state() == BookingState.CANCELLED
                    && FaultReservationCancellation.VEHICLE_FAULT.equals(saved.cancelReason())));
    assertThat(gateway.releases()).containsExactly(PAYMENT_ID);
    verify(payments)
        .save(Payment.depositReleased(PAYMENT_ID, ANA, DEPOSIT, "au_1", BOOKING_ID, NOW));
  }

  @Test
  void cancelReservedBookingOf_vehicleWithoutAReservation_doesNothing() {
    when(bookings.findReservedOfVehicle(BIKE_ID)).thenReturn(Optional.empty());

    cancellation.cancelReservedBookingOf(BIKE_ID);

    verify(bookings, never()).save(any());
    assertThat(gateway.releases()).isEmpty();
  }

  /** The booking stays cancelled; a release that did not go through is not recorded as one. */
  @Test
  void cancelReservedBookingOf_releaseRefused_keepsTheCancellationAndRecordsNoRelease() {
    when(bookings.findReservedOfVehicle(BIKE_ID)).thenReturn(Optional.of(reserved()));
    when(bookings.save(any())).then(returnsFirstArg());
    when(payments.findDepositOfBooking(BOOKING_ID))
        .thenReturn(
            Optional.of(
                Payment.depositAuthorized(PAYMENT_ID, ANA, DEPOSIT, "au_1", BOOKING_ID, NOW)));
    gateway.answeringReleases(new DepositResult.Declined("already_captured"));

    cancellation.cancelReservedBookingOf(BIKE_ID);

    verify(bookings).save(argThat(saved -> saved.state() == BookingState.CANCELLED));
    verify(payments, never()).save(any());
  }
}
