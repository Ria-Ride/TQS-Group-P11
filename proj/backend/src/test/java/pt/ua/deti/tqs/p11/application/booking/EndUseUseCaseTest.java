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
import pt.ua.deti.tqs.p11.application.billing.FakePaymentAdapter.Capture;
import pt.ua.deti.tqs.p11.application.billing.PaymentRepository;
import pt.ua.deti.tqs.p11.application.fleet.HubNotFoundException;
import pt.ua.deti.tqs.p11.application.fleet.HubRepository;
import pt.ua.deti.tqs.p11.application.fleet.LockResult;
import pt.ua.deti.tqs.p11.application.fleet.OpenTickets;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCommandPort;
import pt.ua.deti.tqs.p11.application.fleet.VehicleLifecycleUseCase;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.billing.Payment;
import pt.ua.deti.tqs.p11.domain.booking.Booking;
import pt.ua.deti.tqs.p11.domain.booking.BookingRules;
import pt.ua.deti.tqs.p11.domain.booking.BookingState;
import pt.ua.deti.tqs.p11.domain.booking.BookingTerms;
import pt.ua.deti.tqs.p11.domain.booking.BookingTransitionNotAllowedException;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleStateChange;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.support.TestRules;

/**
 * Ending a ride (US06). The charge itself is tested in OvertimeTest, SettlementTest and
 * BookingTest; here, the order of the steps and what each outcome leaves behind: a full hub refuses
 * before the vehicle is locked and records the first attempt (BR14, BR15); the vehicle is locked
 * before anything changes, and a lock that does not happen leaves the ride going; once the booking
 * is stored, the deposit is captured up to the charge and the rest released (BR13).
 */
@ExtendWith(MockitoExtension.class)
class EndUseUseCaseTest {

  private static final Instant STARTED = Instant.parse("2026-11-26T10:00:00Z");
  private static final Instant PLANNED_END = STARTED.plus(Duration.ofMinutes(60));
  private static final UUID ANA = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000001");
  private static final UUID BRUNO = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000002");
  private static final UUID BIKE_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000001");
  private static final UUID PAYMENT_ID = UUID.fromString("5e6f7081-0000-4000-8000-000000000001");
  private static final UUID BOOKING_ID = UUID.fromString("6f708192-0000-4000-8000-000000000001");
  private static final FleetCode BIKE_CODE = FleetCode.of("bike-0001");
  private static final Money DEPOSIT = Money.of("30.00");

  /** Deposit 30.00; grace 10 min, block 15 min, 2.00 per block; repositioning window 15 min. */
  private static final BookingRules RULES =
      BookingRules.of(TestRules.bookingRules(), VehicleType.E_BIKE);

  private static final Hub HUB_03 = hub("hub-03", 40.6405, 1);
  private static final Hub HUB_05 = hub("hub-05", 40.6432, 10);
  private static final Hub HUB_07 = hub("hub-07", 40.6410, 1);
  private static final Hub HUB_09 = hub("hub-09", 40.6675, 10);
  private static final Hub HUB_11 = hub("hub-11", 40.7000, 10);

  @Mock private VehicleRepository vehicles;
  @Mock private HubRepository hubs;
  @Mock private VehicleStateChangeRepository history;
  @Mock private OpenTickets openTickets;
  @Mock private BookingRepository bookings;
  @Mock private PaymentRepository payments;
  @Mock private VehicleCommandPort commands;

  private final FakePaymentAdapter gateway = new FakePaymentAdapter();

  // ------------------------------------------------------------- fixtures

  private static Hub hub(String code, double latitude, int capacity) {
    return new Hub(
        UUID.nameUUIDFromBytes(code.getBytes()),
        FleetCode.of(code),
        "Hub " + code,
        new GeoPosition(latitude, -8.6538),
        capacity);
  }

  private EndUseUseCase at(Instant now) {
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    DirectTransactionRunner transactions = new DirectTransactionRunner();
    VehicleLifecycleUseCase fleet =
        new VehicleLifecycleUseCase(
            vehicles, hubs, history, openTickets, transactions, clock, UUID::randomUUID);
    return new EndUseUseCase(
        fleet,
        bookings,
        hubs,
        vehicles,
        commands,
        new DepositSettlement(payments, gateway, clock),
        transactions,
        clock);
  }

  private static Instant minutesLate(long minutes) {
    return PLANNED_END.plus(Duration.ofMinutes(minutes));
  }

  private static Booking active() {
    return Booking.startNow(
        BOOKING_ID,
        new BookingTerms(ANA, BIKE_ID, UUID.randomUUID(), HUB_03.id(), STARTED, PLANNED_END),
        RULES,
        "au_1");
  }

  private static Requester ana() {
    return new Requester(ANA, false);
  }

  private static Vehicle inUse() {
    return new Vehicle(
        BIKE_ID, BIKE_CODE, VehicleType.E_BIKE, VehicleState.IN_USE, null, BatteryLevel.of(70));
  }

  private void givenTheRide(Booking booking) {
    when(bookings.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
    lenient().when(vehicles.findById(BIKE_ID)).thenReturn(Optional.of(inUse()));
    lenient().when(bookings.save(any())).then(returnsFirstArg());
  }

  /** The hub has room now and still has it once locked; the vehicle locks; the deposit is held. */
  private void givenAnEndThatGoesThrough(Hub hub) {
    when(hubs.findById(hub.id())).thenReturn(Optional.of(hub));
    when(vehicles.countAtHub(hub.id())).thenReturn(0);
    when(commands.lock(BIKE_CODE)).thenReturn(new LockResult.Locked());
    when(vehicles.findByIdForUpdate(BIKE_ID)).thenReturn(Optional.of(inUse()));
    when(hubs.findByIdForUpdate(hub.id())).thenReturn(Optional.of(hub));
    when(vehicles.save(any())).then(returnsFirstArg());
    when(payments.findDepositOfBooking(BOOKING_ID))
        .thenReturn(
            Optional.of(
                Payment.depositAuthorized(PAYMENT_ID, ANA, DEPOSIT, "au_1", BOOKING_ID, STARTED)));
  }

  private Booking endAt(Instant now, Hub hub) {
    return at(now).end(BOOKING_ID, hub.id(), ana());
  }

  // ------------------------------------------------------------- an end that goes through

  /** BR14: also at a hub other than the one the ride started from. */
  @Test
  void end_atAHubWithAFreeSlot_completesTheBookingAndMakesTheVehicleAvailableThere() {
    Instant now = minutesLate(0);
    givenTheRide(active());
    givenAnEndThatGoesThrough(HUB_05);

    Booking ended = endAt(now, HUB_05);

    assertThat(ended.state()).isEqualTo(BookingState.COMPLETED);
    assertThat(ended.endHubId()).isEqualTo(HUB_05.id());
    assertThat(ended.endedAt()).isEqualTo(now);
    verify(bookings).save(ended);
    verify(history)
        .save(new VehicleStateChange(BIKE_ID, VehicleState.IN_USE, VehicleState.AVAILABLE, now));
    verify(vehicles).save(argThat(saved -> HUB_05.id().equals(saved.hubId())));
  }

  /** The vehicle is locked before anything changes; the money moves only once it is stored. */
  @Test
  void end_locksTheVehicleFirstAndSettlesTheDepositLast() {
    givenTheRide(active());
    givenAnEndThatGoesThrough(HUB_05);

    endAt(minutesLate(26), HUB_05);

    InOrder order = inOrder(commands, vehicles, bookings, payments);
    order.verify(commands).lock(BIKE_CODE);
    order.verify(vehicles).save(any());
    order.verify(bookings).save(any());
    order.verify(payments).save(any());
  }

  /** BR12, BR13: 26 minutes late is 4.00, taken from the deposit; the gateway gives back 26.00. */
  @Test
  void end_withOvertime_capturesTheChargeFromTheDeposit() {
    Instant now = minutesLate(26);
    givenTheRide(active());
    givenAnEndThatGoesThrough(HUB_05);

    endAt(now, HUB_05);

    assertThat(gateway.captures()).containsExactly(new Capture(PAYMENT_ID, Money.of("4.00")));
    assertThat(gateway.releases()).isEmpty();
    verify(payments)
        .save(
            Payment.depositCaptured(
                PAYMENT_ID, ANA, DEPOSIT, Money.of("4.00"), "au_1", BOOKING_ID, now));
  }

  /** BR13: a charge above the deposit takes the whole deposit and nothing more. */
  @Test
  void end_chargeAboveTheDeposit_capturesTheWholeDeposit() {
    givenTheRide(active());
    givenAnEndThatGoesThrough(HUB_05);

    endAt(minutesLate(251), HUB_05);

    assertThat(gateway.captures()).containsExactly(new Capture(PAYMENT_ID, DEPOSIT));
  }

  /** BR12, BR13: within the grace there is nothing to take, so the whole deposit is released. */
  @Test
  void end_withinTheGrace_releasesTheWholeDeposit() {
    Instant now = minutesLate(10);
    givenTheRide(active());
    givenAnEndThatGoesThrough(HUB_05);

    endAt(now, HUB_05);

    assertThat(gateway.captures()).isEmpty();
    assertThat(gateway.releases()).containsExactly(PAYMENT_ID);
    verify(payments)
        .save(Payment.depositReleased(PAYMENT_ID, ANA, DEPOSIT, "au_1", BOOKING_ID, now));
  }

  /** BR15: refused at a full hub 20 min late, ended 15 min later: charged until the refusal. */
  @Test
  void end_withinTheWindowAfterAFullHub_chargesOnlyUntilTheRefusal() {
    givenTheRide(active().recordFullHubAttempt(minutesLate(20)));
    givenAnEndThatGoesThrough(HUB_05);

    endAt(minutesLate(35), HUB_05);

    assertThat(gateway.captures()).containsExactly(new Capture(PAYMENT_ID, Money.of("2.00")));
  }

  /** The ride has ended; a capture that did not go through is logged, not recorded as done. */
  @Test
  void end_captureGetsNoAnswer_keepsTheEndAndRecordsNoPayment() {
    givenTheRide(active());
    givenAnEndThatGoesThrough(HUB_05);
    gateway.answeringCaptures(new DepositResult.TimedOut());

    Booking ended = endAt(minutesLate(26), HUB_05);

    assertThat(ended.state()).isEqualTo(BookingState.COMPLETED);
    verify(payments, never()).save(any());
  }

  // ------------------------------------------------------------- a full hub (BR14, BR15)

  private void givenTheHubsAndTheirVehicles() {
    when(hubs.findById(HUB_07.id())).thenReturn(Optional.of(HUB_07));
    when(hubs.findAll()).thenReturn(List.of(HUB_03, HUB_05, HUB_07, HUB_09, HUB_11));
    when(vehicles.countAtHub(HUB_07.id())).thenReturn(1);
    lenient().when(vehicles.countAtHub(HUB_03.id())).thenReturn(1);
    lenient().when(vehicles.countAtHub(HUB_05.id())).thenReturn(4);
    lenient().when(vehicles.countAtHub(HUB_09.id())).thenReturn(0);
    lenient().when(vehicles.countAtHub(HUB_11.id())).thenReturn(9);
  }

  @Test
  void end_atAFullHub_isRefusedWithTheNearestHubsThatHaveRoom() {
    givenTheRide(active());
    givenTheHubsAndTheirVehicles();
    EndUseUseCase useCase = at(minutesLate(20));

    assertThatThrownBy(() -> useCase.end(BOOKING_ID, HUB_07.id(), ana()))
        .isInstanceOfSatisfying(
            EndAtFullHubException.class,
            e -> {
              assertThat(e.hub()).isEqualTo(HUB_07.code());
              // hub-03 is nearer but full (1 of 1), so it is left out; at most three hubs.
              assertThat(e.nearby())
                  .extracting(nearby -> nearby.hub().code().value())
                  .containsExactly("hub-05", "hub-09", "hub-11");
              assertThat(e.nearby()).extracting(NearbyHub::freeSlots).containsExactly(6, 10, 1);
            });
  }

  @Test
  void end_atAFullHub_recordsTheAttemptAndKeepsTheRideGoing() {
    givenTheRide(active());
    givenTheHubsAndTheirVehicles();
    EndUseUseCase useCase = at(minutesLate(20));

    assertThatThrownBy(() -> useCase.end(BOOKING_ID, HUB_07.id(), ana()))
        .isInstanceOf(EndAtFullHubException.class);

    verify(bookings)
        .save(
            argThat(saved ->
                saved.state() == BookingState.ACTIVE
                    && minutesLate(20).equals(saved.fullHubAttemptAt())));
    verifyNoInteractions(commands, payments);
    verify(vehicles, never()).save(any());
  }

  // ------------------------------------------------------------- the vehicle is not locked

  @Test
  void end_lockRefusedByTheVehicle_isRefusedAndTheRideGoesOn() {
    givenTheRide(active());
    when(hubs.findById(HUB_05.id())).thenReturn(Optional.of(HUB_05));
    when(vehicles.countAtHub(HUB_05.id())).thenReturn(0);
    when(commands.lock(BIKE_CODE)).thenReturn(new LockResult.Refused("NOT_AT_HUB"));
    EndUseUseCase useCase = at(minutesLate(5));

    assertThatThrownBy(() -> useCase.end(BOOKING_ID, HUB_05.id(), ana()))
        .isInstanceOfSatisfying(
            VehicleNotLockedException.class, e -> assertThat(e.reason()).isEqualTo("NOT_AT_HUB"));
    verify(vehicles, never()).save(any());
    verify(bookings, never()).save(any());
    verifyNoInteractions(payments);
  }

  @Test
  void end_lockNotAnswered_isRefusedAsTimedOutAndTheRideGoesOn() {
    givenTheRide(active());
    when(hubs.findById(HUB_05.id())).thenReturn(Optional.of(HUB_05));
    when(vehicles.countAtHub(HUB_05.id())).thenReturn(0);
    when(commands.lock(BIKE_CODE)).thenReturn(new LockResult.NoAnswer());
    EndUseUseCase useCase = at(minutesLate(5));

    assertThatThrownBy(() -> useCase.end(BOOKING_ID, HUB_05.id(), ana()))
        .isInstanceOf(VehicleLockTimedOutException.class);
    verify(bookings, never()).save(any());
    verifyNoInteractions(payments);
  }

  // ------------------------------------------------------------- refused before anything

  @Test
  void end_reservedBooking_isRefusedBeforeTheVehicleIsLocked() {
    Instant start = PLANNED_END;
    givenTheRide(
        Booking.reserve(
            BOOKING_ID,
            new BookingTerms(
                ANA, BIKE_ID, UUID.randomUUID(), HUB_03.id(), start, start.plusSeconds(3600)),
            RULES,
            "au_1"));
    EndUseUseCase useCase = at(STARTED);

    assertThatThrownBy(() -> useCase.end(BOOKING_ID, HUB_05.id(), ana()))
        .isInstanceOf(BookingTransitionNotAllowedException.class);
    verifyNoInteractions(commands, payments);
  }

  /** BR23. */
  @Test
  void end_rideOfAnotherTourist_isRefusedBeforeAnything() {
    givenTheRide(active());
    EndUseUseCase useCase = at(minutesLate(0));
    Requester bruno = new Requester(BRUNO, false);

    assertThatThrownBy(() -> useCase.end(BOOKING_ID, HUB_05.id(), bruno))
        .isInstanceOf(BookingNotOwnedException.class);
    verifyNoInteractions(commands, payments, hubs);
  }

  @Test
  void end_unknownHub_isNotFound() {
    givenTheRide(active());
    UUID nowhere = UUID.randomUUID();
    when(hubs.findById(nowhere)).thenReturn(Optional.empty());
    EndUseUseCase useCase = at(minutesLate(0));

    assertThatThrownBy(() -> useCase.end(BOOKING_ID, nowhere, ana()))
        .isInstanceOf(HubNotFoundException.class);
    verifyNoInteractions(commands);
  }
}
