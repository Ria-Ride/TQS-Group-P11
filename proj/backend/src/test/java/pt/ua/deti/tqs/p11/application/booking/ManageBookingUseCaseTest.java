package pt.ua.deti.tqs.p11.application.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.application.DirectTransactionRunner;
import pt.ua.deti.tqs.p11.application.billing.DepositResult;
import pt.ua.deti.tqs.p11.application.billing.FakePaymentAdapter;
import pt.ua.deti.tqs.p11.application.billing.PaymentRepository;
import pt.ua.deti.tqs.p11.application.fleet.HubRepository;
import pt.ua.deti.tqs.p11.application.fleet.OpenTickets;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCommandPort;
import pt.ua.deti.tqs.p11.application.fleet.VehicleLifecycleUseCase;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.billing.Payment;
import pt.ua.deti.tqs.p11.domain.booking.Booking;
import pt.ua.deti.tqs.p11.domain.booking.BookingRefusal;
import pt.ua.deti.tqs.p11.domain.booking.BookingRefusedException;
import pt.ua.deti.tqs.p11.domain.booking.BookingRules;
import pt.ua.deti.tqs.p11.domain.booking.BookingState;
import pt.ua.deti.tqs.p11.domain.booking.BookingTerms;
import pt.ua.deti.tqs.p11.domain.booking.BookingTransitionNotAllowedException;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleStateChange;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.support.TestRules;

/**
 * What happens to a Reserved booking after it is created (US05): it is started in its window (BR7),
 * cancelled by its tourist (BR13, BR23), or expired when nobody starts it (BR7). The window itself
 * is tested in BookingTest; here, what each outcome changes and what it leaves untouched. The fleet
 * use case is the real one, on mocked repositories, so the vehicle is always locked before the
 * booking is read again.
 */
@ExtendWith(MockitoExtension.class)
class ManageBookingUseCaseTest {

  private static final Instant BOOKED_AT = Instant.parse("2026-11-26T10:00:00Z");
  private static final Instant PLANNED_START = BOOKED_AT.plus(Duration.ofHours(1));
  private static final UUID ANA = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000001");
  private static final UUID BRUNO = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000002");
  private static final UUID BIKE_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000001");
  private static final UUID HUB_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000103");
  private static final UUID PASS_ID = UUID.fromString("4d5e6f70-0000-4000-8000-000000000001");
  private static final UUID PAYMENT_ID = UUID.fromString("5e6f7081-0000-4000-8000-000000000001");
  private static final UUID BOOKING_ID = UUID.fromString("6f708192-0000-4000-8000-000000000001");
  private static final FleetCode BIKE_CODE = FleetCode.of("bike-0001");

  /** Deposit 30.00; start window 15 minutes. */
  private static final BookingRules RULES =
      BookingRules.of(TestRules.bookingRules(), VehicleType.E_BIKE);

  private static final Money DEPOSIT = Money.of("30.00");
  private static final Payment HELD =
      Payment.depositAuthorized(PAYMENT_ID, ANA, DEPOSIT, "au_1", BOOKING_ID, BOOKED_AT);

  @Mock private VehicleRepository vehicles;
  @Mock private HubRepository hubs;
  @Mock private VehicleStateChangeRepository history;
  @Mock private OpenTickets openTickets;
  @Mock private BookingRepository bookings;
  @Mock private PaymentRepository payments;
  @Mock private VehicleCommandPort commands;

  private final FakePaymentAdapter gateway = new FakePaymentAdapter();

  // ------------------------------------------------------------- fixtures

  private ManageBookingUseCase at(Instant now) {
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    DirectTransactionRunner transactions = new DirectTransactionRunner();
    VehicleLifecycleUseCase fleet =
        new VehicleLifecycleUseCase(
            vehicles, hubs, history, openTickets, transactions, clock, UUID::randomUUID);
    return new ManageBookingUseCase(
        fleet,
        bookings,
        new DepositReturn(payments, gateway, clock),
        commands,
        transactions,
        clock);
  }

  private static Booking reserved() {
    return Booking.reserve(
        BOOKING_ID,
        new BookingTerms(
            ANA, BIKE_ID, PASS_ID, HUB_ID, PLANNED_START, PLANNED_START.plus(Duration.ofHours(1))),
        RULES,
        "au_1");
  }

  private static Vehicle vehicleIn(VehicleState state) {
    return new Vehicle(
        BIKE_ID,
        BIKE_CODE,
        VehicleType.E_BIKE,
        state,
        state.isAtHub() ? HUB_ID : null,
        BatteryLevel.of(100));
  }

  private static Requester ana() {
    return new Requester(ANA, false);
  }

  private void givenTheBookingIs(Booking booking) {
    when(bookings.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
  }

  private void givenTheVehicleIsLockedIn(VehicleState state) {
    when(vehicles.findByIdForUpdate(BIKE_ID)).thenReturn(Optional.of(vehicleIn(state)));
    lenient().when(vehicles.save(any())).then(returnsFirstArg());
  }

  private void givenTheDepositIsHeld() {
    when(payments.findDepositOfBooking(BOOKING_ID)).thenReturn(Optional.of(HELD));
  }

  private static Instant minutesAfterThePlannedStart(int minutes) {
    return PLANNED_START.plus(Duration.ofMinutes(minutes));
  }

  // ------------------------------------------------------------- start (BR7)

  @Test
  void start_inTheWindow_makesTheBookingActiveAndTheVehicleInUse() {
    Instant now = minutesAfterThePlannedStart(5);
    givenTheBookingIs(reserved());
    givenTheVehicleIsLockedIn(VehicleState.RESERVED);
    when(bookings.save(any())).then(returnsFirstArg());

    Booking started = at(now).start(BOOKING_ID, ana());

    assertThat(started.state()).isEqualTo(BookingState.ACTIVE);
    assertThat(started.startedAt()).isEqualTo(now);
    verify(bookings).save(started);
    verify(history)
        .save(new VehicleStateChange(BIKE_ID, VehicleState.RESERVED, VehicleState.IN_USE, now));
  }

  /** As in US04: the unlock goes out once the booking is stored; the deposit stays held. */
  @Test
  void start_inTheWindow_unlocksTheVehicleAfterStoringAndKeepsTheDeposit() {
    givenTheBookingIs(reserved());
    givenTheVehicleIsLockedIn(VehicleState.RESERVED);
    when(bookings.save(any())).then(returnsFirstArg());

    at(minutesAfterThePlannedStart(0)).start(BOOKING_ID, ana());

    InOrder order = inOrder(bookings, commands);
    order.verify(bookings).save(any());
    order.verify(commands).unlock(BIKE_CODE);
    assertThat(gateway.releases()).isEmpty();
    verifyNoInteractions(payments);
  }

  @Test
  void start_tooEarly_isRefusedAndChangesNothing() {
    givenTheBookingIs(reserved());
    givenTheVehicleIsLockedIn(VehicleState.RESERVED);
    ManageBookingUseCase early = at(PLANNED_START.minusSeconds(1));

    assertThatThrownBy(() -> early.start(BOOKING_ID, ana()))
        .isInstanceOfSatisfying(
            BookingRefusedException.class,
            e -> assertThat(e.reason()).isEqualTo(BookingRefusal.TOO_EARLY_TO_START));
    verify(vehicles, never()).save(any());
    verify(bookings, never()).save(any());
    verifyNoInteractions(commands);
  }

  /** BR23: someone else's booking is refused before anything is locked. */
  @Test
  void start_bookingOfAnotherTourist_isRefusedAndChangesNothing() {
    givenTheBookingIs(reserved());
    ManageBookingUseCase inTheWindow = at(minutesAfterThePlannedStart(1));
    Requester bruno = new Requester(BRUNO, false);

    assertThatThrownBy(() -> inTheWindow.start(BOOKING_ID, bruno))
        .isInstanceOf(BookingNotOwnedException.class);
    verify(vehicles, never()).findByIdForUpdate(any());
    verifyNoInteractions(commands);
  }

  /** The access matrix lets an admin act on any booking. */
  @Test
  void start_byAnAdmin_isAllowedOnAnyBooking() {
    givenTheBookingIs(reserved());
    givenTheVehicleIsLockedIn(VehicleState.RESERVED);
    when(bookings.save(any())).then(returnsFirstArg());

    Booking started =
        at(minutesAfterThePlannedStart(1)).start(BOOKING_ID, new Requester(BRUNO, true));

    assertThat(started.state()).isEqualTo(BookingState.ACTIVE);
  }

  @Test
  void start_unknownBooking_isNotFound() {
    when(bookings.findById(BOOKING_ID)).thenReturn(Optional.empty());
    ManageBookingUseCase now = at(PLANNED_START);

    assertThatThrownBy(() -> now.start(BOOKING_ID, ana()))
        .isInstanceOf(BookingNotFoundException.class);
  }

  // ------------------------------------------------------------- cancel (BR13)

  @Test
  void cancel_reservedBooking_cancelsItAndGivesTheVehicleBack() {
    Instant now = BOOKED_AT.plus(Duration.ofMinutes(10));
    givenTheBookingIs(reserved());
    givenTheVehicleIsLockedIn(VehicleState.RESERVED);
    givenTheDepositIsHeld();
    when(bookings.save(any())).then(returnsFirstArg());

    Booking cancelled = at(now).cancel(BOOKING_ID, ana());

    assertThat(cancelled.state()).isEqualTo(BookingState.CANCELLED);
    assertThat(cancelled.cancelReason()).isEqualTo(ManageBookingUseCase.CANCELLED_BY_TOURIST);
    verify(history)
        .save(new VehicleStateChange(BIKE_ID, VehicleState.RESERVED, VehicleState.AVAILABLE, now));
  }

  @Test
  void cancel_reservedBooking_releasesTheWholeDepositAfterStoring() {
    Instant now = BOOKED_AT.plus(Duration.ofMinutes(10));
    givenTheBookingIs(reserved());
    givenTheVehicleIsLockedIn(VehicleState.RESERVED);
    givenTheDepositIsHeld();
    when(bookings.save(any())).then(returnsFirstArg());

    at(now).cancel(BOOKING_ID, ana());

    assertThat(gateway.releases()).containsExactly(PAYMENT_ID);
    assertThat(gateway.captures()).isEmpty();
    InOrder order = inOrder(bookings, payments);
    order.verify(bookings).save(any());
    order
        .verify(payments)
        .save(Payment.depositReleased(PAYMENT_ID, ANA, DEPOSIT, "au_1", BOOKING_ID, now));
  }

  @Test
  void cancel_activeBooking_isRefusedAndKeepsTheDeposit() {
    Instant now = minutesAfterThePlannedStart(5);
    givenTheBookingIs(reserved().start(now));
    givenTheVehicleIsLockedIn(VehicleState.IN_USE);
    ManageBookingUseCase useCase = at(now);

    assertThatThrownBy(() -> useCase.cancel(BOOKING_ID, ana()))
        .isInstanceOf(BookingTransitionNotAllowedException.class);
    verify(vehicles, never()).save(any());
    assertThat(gateway.releases()).isEmpty();
  }

  @Test
  void cancel_bookingOfAnotherTourist_isRefusedAndKeepsTheDeposit() {
    givenTheBookingIs(reserved());
    ManageBookingUseCase useCase = at(BOOKED_AT);
    Requester bruno = new Requester(BRUNO, false);

    assertThatThrownBy(() -> useCase.cancel(BOOKING_ID, bruno))
        .isInstanceOf(BookingNotOwnedException.class);
    verify(vehicles, never()).findByIdForUpdate(any());
    assertThat(gateway.releases()).isEmpty();
  }

  /** The booking stays cancelled; a release that did not go through is not recorded as one. */
  @Test
  void cancel_releaseGetsNoAnswer_keepsTheCancellationAndRecordsNoRelease() {
    givenTheBookingIs(reserved());
    givenTheVehicleIsLockedIn(VehicleState.RESERVED);
    givenTheDepositIsHeld();
    when(bookings.save(any())).then(returnsFirstArg());
    gateway.answeringReleases(new DepositResult.TimedOut());

    Booking cancelled = at(BOOKED_AT).cancel(BOOKING_ID, ana());

    assertThat(cancelled.state()).isEqualTo(BookingState.CANCELLED);
    verify(payments, never()).save(any());
  }

  // ------------------------------------------------------------- expiry (BR7)

  @Test
  void expireOverdue_expiresEveryOverdueReservationAndGivesEverythingBack() {
    Instant now = minutesAfterThePlannedStart(15).plusSeconds(1);
    when(bookings.findReservedStartingBefore(now)).thenReturn(List.of(reserved()));
    givenTheBookingIs(reserved());
    givenTheVehicleIsLockedIn(VehicleState.RESERVED);
    givenTheDepositIsHeld();
    when(bookings.save(any())).then(returnsFirstArg());

    int expired = at(now).expireOverdue();

    assertThat(expired).isEqualTo(1);
    verify(bookings).save(argThat(saved -> saved.state() == BookingState.EXPIRED));
    verify(history)
        .save(new VehicleStateChange(BIKE_ID, VehicleState.RESERVED, VehicleState.AVAILABLE, now));
    assertThat(gateway.releases()).containsExactly(PAYMENT_ID);
    verify(payments)
        .save(Payment.depositReleased(PAYMENT_ID, ANA, DEPOSIT, "au_1", BOOKING_ID, now));
  }

  /** At +15:00 the window is still open: the reservation is not touched. */
  @Test
  void expireOverdue_reservationAtTheEndOfItsWindow_isKept() {
    Instant now = minutesAfterThePlannedStart(15);
    when(bookings.findReservedStartingBefore(now)).thenReturn(List.of(reserved()));

    assertThat(at(now).expireOverdue()).isZero();
    verify(vehicles, never()).findByIdForUpdate(any());
    verify(bookings, never()).save(any());
    assertThat(gateway.releases()).isEmpty();
  }

  /**
   * Started (or cancelled) between the search and the lock: read again with the vehicle locked, it
   * is no longer overdue, so it is skipped. This is also why a second run changes nothing.
   */
  @Test
  void expireOverdue_reservationStartedMeanwhile_isSkipped() {
    Instant now = minutesAfterThePlannedStart(20);
    when(bookings.findReservedStartingBefore(now)).thenReturn(List.of(reserved()));
    givenTheBookingIs(reserved().start(minutesAfterThePlannedStart(10)));
    givenTheVehicleIsLockedIn(VehicleState.IN_USE);

    assertThat(at(now).expireOverdue()).isZero();
    verify(vehicles, never()).save(any());
    verify(bookings, never()).save(any());
    assertThat(gateway.releases()).isEmpty();
  }
}
