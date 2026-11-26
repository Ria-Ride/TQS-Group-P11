package pt.ua.deti.tqs.p11.application.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import pt.ua.deti.tqs.p11.application.fleet.VehicleNotFoundException;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.billing.Payment;
import pt.ua.deti.tqs.p11.domain.booking.Booking;
import pt.ua.deti.tqs.p11.domain.booking.BookingPolicy;
import pt.ua.deti.tqs.p11.domain.booking.BookingRefusal;
import pt.ua.deti.tqs.p11.domain.booking.BookingRefusedException;
import pt.ua.deti.tqs.p11.domain.booking.BookingState;
import pt.ua.deti.tqs.p11.domain.booking.BookingTerms;
import pt.ua.deti.tqs.p11.domain.booking.PassCoverage;
import pt.ua.deti.tqs.p11.domain.booking.UsageRules;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryRule;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleStateChange;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.User;

/**
 * Starting an immediate use (US04). Which requests the rules accept is tested in BookingPolicyTest;
 * here, the order of the steps and what each failure leaves behind: the rules are checked before
 * any money is held, the deposit is held before any state change (BR11), the booking and the
 * vehicle change together on the locked rows (BR3, BR6), and the vehicle is unlocked only after the
 * booking is stored. The fleet use case is the real one, on mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
class StartUseUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
  private static final UUID ANA = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000001");
  private static final UUID BIKE_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000001");
  private static final UUID HUB_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000103");
  private static final UUID PASS_ID = UUID.fromString("4d5e6f70-0000-4000-8000-000000000001");
  private static final UUID PAYMENT_ID = UUID.fromString("5e6f7081-0000-4000-8000-000000000001");
  private static final UUID BOOKING_ID = UUID.fromString("6f708192-0000-4000-8000-000000000001");
  private static final FleetCode BIKE_CODE = FleetCode.of("bike-0001");
  private static final User ANA_ACCOUNT =
      User.guest(ANA, "Ana Lopes", new EmailAddress("ana.lopes@example.com"), "hash");
  private static final UsageRules RULES =
      new UsageRules(
          Duration.ofHours(4),
          Map.of(VehicleType.E_BIKE, Money.of("30.00"), VehicleType.E_SCOOTER, Money.of("50.00")),
          Duration.ofMinutes(15),
          Duration.ofMinutes(10),
          Duration.ofMinutes(15),
          Money.of("2.00"));
  private static final BatteryRule BATTERY =
      new BatteryRule(BatteryLevel.of(20), Map.of(VehicleType.E_BIKE, 5, VehicleType.E_SCOOTER, 5));
  private static final PassCoverage DAY_PASS =
      new PassCoverage(
          PASS_ID,
          NOW.minus(Duration.ofHours(1)),
          NOW.plus(Duration.ofHours(23)),
          Set.of(VehicleType.E_BIKE, VehicleType.E_SCOOTER));

  @Mock private VehicleRepository vehicles;
  @Mock private HubRepository hubs;
  @Mock private VehicleStateChangeRepository history;
  @Mock private OpenTickets openTickets;
  @Mock private UserRepository users;
  @Mock private PassCoverages passes;
  @Mock private BookingRepository bookings;
  @Mock private PaymentRepository payments;
  @Mock private VehicleCommandPort commands;

  private final FakePaymentAdapter gateway = new FakePaymentAdapter();
  private StartUseUseCase useCase;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    DirectTransactionRunner transactions = new DirectTransactionRunner();
    VehicleLifecycleUseCase fleet =
        new VehicleLifecycleUseCase(
            vehicles, hubs, history, openTickets, transactions, clock, UUID::randomUUID);
    Iterator<UUID> ids = List.of(PAYMENT_ID, BOOKING_ID).iterator();
    useCase =
        new StartUseUseCase(
            fleet,
            users,
            passes,
            bookings,
            payments,
            gateway,
            commands,
            () -> new BookingPolicy(RULES, BATTERY),
            transactions,
            clock,
            ids::next);
  }

  // ------------------------------------------------------------- fixtures

  private static Vehicle available(VehicleType type) {
    return new Vehicle(
        BIKE_ID, BIKE_CODE, type, VehicleState.AVAILABLE, HUB_ID, BatteryLevel.of(100));
  }

  private static StartUseCommand rideFor(int minutes) {
    return new StartUseCommand(ANA, BIKE_ID, NOW.plus(Duration.ofMinutes(minutes)), "tok_approve");
  }

  /** Step 1, the checks without locks: the vehicle, the tourist's passes and bookings. */
  private void givenTheRequestIsRead(Vehicle vehicle) {
    when(vehicles.findById(BIKE_ID)).thenReturn(Optional.of(vehicle));
    when(passes.ofAccount(ANA)).thenReturn(List.of(DAY_PASS));
    when(bookings.findOngoingByAccount(ANA)).thenReturn(List.of());
  }

  /** Step 3, inside the transaction: the account and the vehicle are locked. */
  private void givenTheRowsAreLocked(Vehicle lockedVehicle) {
    when(users.findByIdForUpdate(ANA)).thenReturn(Optional.of(ANA_ACCOUNT));
    when(vehicles.findByIdForUpdate(BIKE_ID)).thenReturn(Optional.of(lockedVehicle));
  }

  private void givenEverythingIsStored() {
    when(vehicles.save(any())).then(returnsFirstArg());
    when(bookings.save(any())).then(returnsFirstArg());
  }

  private Booking aValidStart(VehicleType type) {
    Vehicle vehicle = available(type);
    givenTheRequestIsRead(vehicle);
    givenTheRowsAreLocked(vehicle);
    givenEverythingIsStored();
    return useCase.start(rideFor(60));
  }

  // ------------------------------------------------------------- a valid start

  @Test
  void start_validRequest_createsAnActiveBookingWithTheRulesOfTheType() {
    Booking booking = aValidStart(VehicleType.E_BIKE);

    assertThat(booking.id()).isEqualTo(BOOKING_ID);
    assertThat(booking.state()).isEqualTo(BookingState.ACTIVE);
    assertThat(booking.terms())
        .isEqualTo(
            new BookingTerms(ANA, BIKE_ID, PASS_ID, HUB_ID, NOW, NOW.plus(Duration.ofMinutes(60))));
    assertThat(booking.rules()).isEqualTo(RULES.forType(VehicleType.E_BIKE));
    assertThat(booking.depositReference()).isEqualTo("au_fake");
    verify(bookings).save(booking);
  }

  @Test
  void start_validRequest_putsTheVehicleInUseAndRecordsTheChange() {
    aValidStart(VehicleType.E_BIKE);

    verify(vehicles).save(argThat(saved -> saved.state() == VehicleState.IN_USE));
    verify(history)
        .save(new VehicleStateChange(BIKE_ID, VehicleState.AVAILABLE, VehicleState.IN_USE, NOW));
  }

  /** BR11: the deposit of the vehicle's type, on the tourist's card, with our payment id. */
  @Test
  void start_validRequest_holdsTheDepositOfTheType() {
    aValidStart(VehicleType.E_SCOOTER);

    assertThat(gateway.authorizations())
        .singleElement()
        .satisfies(request -> {
          assertThat(request.paymentId()).isEqualTo(PAYMENT_ID);
          assertThat(request.amount()).isEqualTo(Money.of("50.00"));
          assertThat(request.paymentToken()).isEqualTo("tok_approve");
        });
  }

  /** BR11: the vehicle state changes only after the deposit is held. */
  @Test
  void start_validRequest_holdsTheDepositBeforeTheVehicleChanges() {
    Vehicle bike = available(VehicleType.E_BIKE);
    givenTheRequestIsRead(bike);
    givenTheRowsAreLocked(bike);
    when(bookings.save(any())).then(returnsFirstArg());
    when(vehicles.save(any()))
        .then(saving -> {
          assertThat(gateway.authorizations()).as("deposit held before the change").hasSize(1);
          return saving.getArgument(0);
        });

    useCase.start(rideFor(60));

    verify(vehicles).save(any());
  }

  @Test
  void start_validRequest_recordsTheHeldDepositWithItsBooking() {
    aValidStart(VehicleType.E_BIKE);

    verify(payments)
        .save(
            Payment.depositAuthorized(
                PAYMENT_ID, ANA, Money.of("30.00"), "au_fake", BOOKING_ID, NOW));
  }

  /** Decision 7: the unlock goes out only once the booking is stored, and is not waited for. */
  @Test
  void start_validRequest_unlocksTheVehicleAfterTheBookingIsStored() {
    aValidStart(VehicleType.E_BIKE);

    InOrder order = inOrder(bookings, commands);
    order.verify(bookings).save(any());
    order.verify(commands).unlock(BIKE_CODE);
  }

  /** D2: the account is locked before the vehicle, always in that order. */
  @Test
  void start_validRequest_locksTheAccountBeforeTheVehicle() {
    aValidStart(VehicleType.E_BIKE);

    InOrder order = inOrder(users, vehicles);
    order.verify(users).findByIdForUpdate(ANA);
    order.verify(vehicles).findByIdForUpdate(BIKE_ID);
  }

  // ------------------------------------------------------------- refused before any money

  @Test
  void start_refusedByTheRules_holdsNoMoneyAndChangesNothing() {
    givenTheRequestIsRead(available(VehicleType.E_BIKE));
    StartUseCommand tooLong = rideFor(241);

    assertThatThrownBy(() -> useCase.start(tooLong))
        .isInstanceOfSatisfying(
            BookingRefusedException.class,
            e -> assertThat(e.reason()).isEqualTo(BookingRefusal.DURATION_NOT_ALLOWED));
    assertThat(gateway.authorizations()).isEmpty();
    verify(vehicles, never()).save(any());
    verifyNoInteractions(payments, commands);
  }

  @Test
  void start_unknownVehicle_isNotFoundAndHoldsNoMoney() {
    when(vehicles.findById(BIKE_ID)).thenReturn(Optional.empty());
    StartUseCommand ride = rideFor(60);

    assertThatThrownBy(() -> useCase.start(ride)).isInstanceOf(VehicleNotFoundException.class);
    assertThat(gateway.authorizations()).isEmpty();
  }

  // ------------------------------------------------------------- the deposit is not held

  /** BR11: declined, so no booking and no state change; the attempt is kept. */
  @Test
  void start_depositDeclined_createsNothingAndKeepsTheDecline() {
    givenTheRequestIsRead(available(VehicleType.E_BIKE));
    gateway.answeringAuthorizations(new DepositResult.Declined("card_declined"));
    StartUseCommand ride = rideFor(60);

    assertThatThrownBy(() -> useCase.start(ride))
        .isInstanceOfSatisfying(
            DepositDeclinedException.class, e -> assertThat(e.reason()).isEqualTo("card_declined"));
    verify(payments)
        .save(Payment.depositDeclined(PAYMENT_ID, ANA, Money.of("30.00"), "card_declined", NOW));
    verify(vehicles, never()).save(any());
    verify(bookings, never()).save(any());
    verifyNoInteractions(commands);
  }

  /**
   * BR11, decision 5: no answer in time, so the gateway may hold the deposit; it is released with
   * the same payment id, and nothing is created.
   */
  @Test
  void start_depositTimedOut_releasesItAndCreatesNothing() {
    givenTheRequestIsRead(available(VehicleType.E_BIKE));
    gateway.answeringAuthorizations(new DepositResult.TimedOut());
    StartUseCommand ride = rideFor(60);

    assertThatThrownBy(() -> useCase.start(ride)).isInstanceOf(DepositTimedOutException.class);
    assertThat(gateway.releases()).containsExactly(PAYMENT_ID);
    verify(payments)
        .save(Payment.depositDeclined(PAYMENT_ID, ANA, Money.of("30.00"), "timeout", NOW));
    verify(vehicles, never()).save(any());
    verify(bookings, never()).save(any());
    verifyNoInteractions(commands);
  }

  // ------------------------------------------------------------- lost on the locked rows

  /**
   * BR3: between the first check and the lock another tourist took the vehicle. The rules are
   * checked again on the locked vehicle; the deposit is given back and nothing is created.
   */
  @Test
  void start_vehicleTakenMeanwhile_releasesTheDepositAndCreatesNothing() {
    givenTheRequestIsRead(available(VehicleType.E_BIKE));
    Vehicle taken =
        new Vehicle(
            BIKE_ID,
            BIKE_CODE,
            VehicleType.E_BIKE,
            VehicleState.IN_USE,
            null,
            BatteryLevel.of(100));
    givenTheRowsAreLocked(taken);
    StartUseCommand ride = rideFor(60);

    assertThatThrownBy(() -> useCase.start(ride))
        .isInstanceOfSatisfying(
            BookingRefusedException.class,
            e -> assertThat(e.reason()).isEqualTo(BookingRefusal.VEHICLE_NOT_AVAILABLE));
    assertThat(gateway.releases()).containsExactly(PAYMENT_ID);
    verify(payments)
        .save(Payment.depositReleased(PAYMENT_ID, ANA, Money.of("30.00"), "au_fake", null, NOW));
    verify(vehicles, never()).save(any());
    verify(bookings, never()).save(any());
    verifyNoInteractions(commands);
  }

  /** BR6, D2: the same tourist started another ride meanwhile; seen once the account is locked. */
  @Test
  void start_otherRideOfTheTouristMeanwhile_releasesTheDepositAndCreatesNothing() {
    Vehicle bike = available(VehicleType.E_BIKE);
    when(vehicles.findById(BIKE_ID)).thenReturn(Optional.of(bike));
    when(passes.ofAccount(ANA)).thenReturn(List.of(DAY_PASS));
    Booking otherRide =
        Booking.startNow(
            UUID.randomUUID(),
            new BookingTerms(
                ANA, UUID.randomUUID(), PASS_ID, HUB_ID, NOW, NOW.plus(Duration.ofMinutes(30))),
            RULES.forType(VehicleType.E_BIKE),
            "au_other");
    when(bookings.findOngoingByAccount(ANA)).thenReturn(List.of(), List.of(otherRide));
    givenTheRowsAreLocked(bike);
    StartUseCommand ride = rideFor(60);

    assertThatThrownBy(() -> useCase.start(ride))
        .isInstanceOfSatisfying(
            BookingRefusedException.class,
            e -> assertThat(e.reason()).isEqualTo(BookingRefusal.OVERLAPPING_BOOKING));
    assertThat(gateway.releases()).containsExactly(PAYMENT_ID);
    verify(bookings, never()).save(any());
  }

  /**
   * A release that does not go through is not recorded as one (the money may still be held, and it
   * is logged); the tourist still gets the real reason.
   */
  @Test
  void start_releaseFailsAfterLosing_stillReportsTheRefusalAndRecordsNoRelease() {
    givenTheRequestIsRead(available(VehicleType.E_BIKE));
    Vehicle taken =
        new Vehicle(
            BIKE_ID,
            BIKE_CODE,
            VehicleType.E_BIKE,
            VehicleState.IN_USE,
            null,
            BatteryLevel.of(100));
    givenTheRowsAreLocked(taken);
    gateway.answeringReleases(new DepositResult.TimedOut());
    StartUseCommand ride = rideFor(60);

    assertThatThrownBy(() -> useCase.start(ride)).isInstanceOf(BookingRefusedException.class);
    verify(payments, never()).save(any());
  }
}
