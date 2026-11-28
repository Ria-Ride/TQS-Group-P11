package pt.ua.deti.tqs.p11.application.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.application.DirectTransactionRunner;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.ForbiddenTransitionException;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;
import pt.ua.deti.tqs.p11.domain.fleet.HubFullException;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleStateChange;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

/**
 * Vehicle use cases (US08). Which transitions are allowed is tested in VehicleStateTest; here, that
 * each operation loads, applies, saves and records the change, and that a refused one saves and
 * records nothing. The vehicle and the hub are looked up "for update": with a plain findById the
 * stubs below would not answer, so these tests also fail if a lock is left out.
 */
@ExtendWith(MockitoExtension.class)
class VehicleLifecycleUseCaseTest {

  private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");
  private static final UUID BIKE_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000009");
  private static final FleetCode BIKE_CODE = FleetCode.of("bike-0009");
  private static final UUID HUB_03_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000103");
  private static final UUID HUB_05_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000105");
  private static final Hub HUB_05 =
      new Hub(
          HUB_05_ID,
          FleetCode.of("hub-05"),
          "Hotel Ria Azul",
          new GeoPosition(40.6421, -8.6524),
          10);
  private static final Hub HUB_03 =
      new Hub(
          HUB_03_ID,
          FleetCode.of("hub-03"),
          "Canal Central",
          new GeoPosition(40.6405, -8.6538),
          10);

  @Mock private VehicleRepository vehicles;
  @Mock private HubRepository hubs;
  @Mock private VehicleStateChangeRepository history;
  @Mock private OpenTickets openTickets;

  private VehicleLifecycleUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new VehicleLifecycleUseCase(
            vehicles,
            hubs,
            history,
            openTickets,
            new DirectTransactionRunner(),
            Clock.fixed(T0, ZoneOffset.UTC),
            () -> BIKE_ID);
  }

  private static Vehicle bikeAtHub03(VehicleState state) {
    return new Vehicle(BIKE_ID, BIKE_CODE, VehicleType.E_BIKE, state, HUB_03_ID);
  }

  private static Vehicle bikeInTransit() {
    return new Vehicle(BIKE_ID, BIKE_CODE, VehicleType.E_BIKE, VehicleState.IN_TRANSIT, null);
  }

  /** The vehicle is found (and locked) and the repository stores what it gets. */
  private void lockedVehicleIs(Vehicle vehicle) {
    when(vehicles.findByIdForUpdate(BIKE_ID)).thenReturn(Optional.of(vehicle));
  }

  private void vehicleSaveReturnsWhatItGets() {
    when(vehicles.save(any(Vehicle.class))).then(returnsFirstArg());
  }

  private void nothingWasSavedOrRecorded() {
    verify(vehicles, never()).save(any());
    verifyNoInteractions(history);
  }

  // ------------------------------------------------------------- register (BR14, BR16)

  private static RegisterVehicleCommand bikeAt(UUID hubId) {
    return new RegisterVehicleCommand("bike-0009", VehicleType.E_BIKE, hubId);
  }

  @Test
  void register_hubWithAFreeSlot_savesADeactivatedVehicleAndRecordsTheRegistration() {
    when(vehicles.existsByCode(BIKE_CODE)).thenReturn(false);
    when(hubs.findByIdForUpdate(HUB_03_ID)).thenReturn(Optional.of(HUB_03));
    when(vehicles.countAtHub(HUB_03_ID)).thenReturn(9);
    vehicleSaveReturnsWhatItGets();

    assertThat(useCase.register(bikeAt(HUB_03_ID)))
        .isEqualTo(bikeAtHub03(VehicleState.DEACTIVATED));
    verify(history).save(VehicleStateChange.registration(BIKE_ID, T0));
  }

  @Test
  void register_fullHub_isRefusedAndNothingIsSaved() {
    when(vehicles.existsByCode(BIKE_CODE)).thenReturn(false);
    when(hubs.findByIdForUpdate(HUB_03_ID)).thenReturn(Optional.of(HUB_03));
    when(vehicles.countAtHub(HUB_03_ID)).thenReturn(10);

    assertThatThrownBy(() -> useCase.register(bikeAt(HUB_03_ID)))
        .isInstanceOf(HubFullException.class);
    nothingWasSavedOrRecorded();
  }

  @Test
  void register_codeAlreadyInUse_isRefusedAndNothingIsSaved() {
    when(vehicles.existsByCode(BIKE_CODE)).thenReturn(true);

    assertThatThrownBy(() -> useCase.register(bikeAt(HUB_03_ID)))
        .isInstanceOf(VehicleCodeAlreadyInUseException.class);
    nothingWasSavedOrRecorded();
  }

  @Test
  void register_unknownHub_isNotFound() {
    when(vehicles.existsByCode(BIKE_CODE)).thenReturn(false);
    when(hubs.findByIdForUpdate(HUB_03_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.register(bikeAt(HUB_03_ID)))
        .isInstanceOf(HubNotFoundException.class);
    nothingWasSavedOrRecorded();
  }

  // ------------------------------------------------------------- activate, deactivate (BR16)

  @Test
  void activate_noOpenTicket_savesTheVehicleAvailableAndRecordsTheChange() {
    lockedVehicleIs(bikeAtHub03(VehicleState.DEACTIVATED));
    when(openTickets.existFor(BIKE_ID)).thenReturn(false);
    vehicleSaveReturnsWhatItGets();

    assertThat(useCase.activate(BIKE_ID)).isEqualTo(bikeAtHub03(VehicleState.AVAILABLE));
    verify(history)
        .save(
            new VehicleStateChange(BIKE_ID, VehicleState.DEACTIVATED, VehicleState.AVAILABLE, T0));
  }

  @Test
  void activate_withAnOpenTicket_isRefusedAndNothingIsSaved() {
    lockedVehicleIs(bikeAtHub03(VehicleState.DEACTIVATED));
    when(openTickets.existFor(BIKE_ID)).thenReturn(true);

    assertThatThrownBy(() -> useCase.activate(BIKE_ID))
        .isInstanceOf(VehicleHasOpenTicketException.class)
        .hasMessageContaining("bike-0009");
    nothingWasSavedOrRecorded();
  }

  @Test
  void activate_unknownVehicle_isNotFound() {
    when(vehicles.findByIdForUpdate(BIKE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.activate(BIKE_ID))
        .isInstanceOf(VehicleNotFoundException.class);
    nothingWasSavedOrRecorded();
  }

  @Test
  void deactivate_availableVehicle_savesItDeactivatedAndRecordsTheChange() {
    lockedVehicleIs(bikeAtHub03(VehicleState.AVAILABLE));
    vehicleSaveReturnsWhatItGets();

    assertThat(useCase.deactivate(BIKE_ID)).isEqualTo(bikeAtHub03(VehicleState.DEACTIVATED));
    verify(history)
        .save(
            new VehicleStateChange(BIKE_ID, VehicleState.AVAILABLE, VehicleState.DEACTIVATED, T0));
  }

  /** BR16: a forbidden transition is refused and not recorded in the history. */
  @Test
  void deactivate_reservedVehicle_isRefusedAndNothingIsSaved() {
    lockedVehicleIs(bikeAtHub03(VehicleState.RESERVED));

    assertThatThrownBy(() -> useCase.deactivate(BIKE_ID))
        .isInstanceOf(ForbiddenTransitionException.class);
    nothingWasSavedOrRecorded();
  }

  // ------------------------------------------------------------- relocation (BR14)

  @Test
  void startMove_availableVehicle_savesItInTransitAndRecordsTheChange() {
    lockedVehicleIs(bikeAtHub03(VehicleState.AVAILABLE));
    vehicleSaveReturnsWhatItGets();

    assertThat(useCase.startMove(BIKE_ID)).isEqualTo(bikeInTransit());
    verify(history)
        .save(new VehicleStateChange(BIKE_ID, VehicleState.AVAILABLE, VehicleState.IN_TRANSIT, T0));
  }

  @Test
  void confirmArrival_destinationWithAFreeSlot_savesItAvailableThere() {
    lockedVehicleIs(bikeInTransit());
    when(hubs.findByIdForUpdate(HUB_05_ID)).thenReturn(Optional.of(HUB_05));
    when(vehicles.countAtHub(HUB_05_ID)).thenReturn(9);
    vehicleSaveReturnsWhatItGets();

    assertThat(useCase.confirmArrival(BIKE_ID, HUB_05_ID))
        .isEqualTo(
            new Vehicle(BIKE_ID, BIKE_CODE, VehicleType.E_BIKE, VehicleState.AVAILABLE, HUB_05_ID));
    verify(history)
        .save(new VehicleStateChange(BIKE_ID, VehicleState.IN_TRANSIT, VehicleState.AVAILABLE, T0));
  }

  @Test
  void confirmArrival_fullDestination_isRefusedAndNothingIsSaved() {
    lockedVehicleIs(bikeInTransit());
    when(hubs.findByIdForUpdate(HUB_05_ID)).thenReturn(Optional.of(HUB_05));
    when(vehicles.countAtHub(HUB_05_ID)).thenReturn(10);

    assertThatThrownBy(() -> useCase.confirmArrival(BIKE_ID, HUB_05_ID))
        .isInstanceOf(HubFullException.class);
    nothingWasSavedOrRecorded();
  }

  @Test
  void confirmArrival_unknownDestination_isNotFound() {
    lockedVehicleIs(bikeInTransit());
    when(hubs.findByIdForUpdate(HUB_05_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.confirmArrival(BIKE_ID, HUB_05_ID))
        .isInstanceOf(HubNotFoundException.class);
    nothingWasSavedOrRecorded();
  }

  @Test
  void confirmArrival_vehicleNotInTransit_isRefusedAndNothingIsSaved() {
    lockedVehicleIs(bikeAtHub03(VehicleState.AVAILABLE));

    assertThatThrownBy(() -> useCase.confirmArrival(BIKE_ID, HUB_05_ID))
        .isInstanceOf(ForbiddenTransitionException.class);
    nothingWasSavedOrRecorded();
  }

  // ------------------------------------------------------------- retirement (BR16)

  @Test
  void retire_availableVehicle_savesItRetiredAndRecordsTheChange() {
    lockedVehicleIs(bikeAtHub03(VehicleState.AVAILABLE));
    vehicleSaveReturnsWhatItGets();

    assertThat(useCase.retire(BIKE_ID))
        .isEqualTo(new Vehicle(BIKE_ID, BIKE_CODE, VehicleType.E_BIKE, VehicleState.RETIRED, null));
    verify(history)
        .save(new VehicleStateChange(BIKE_ID, VehicleState.AVAILABLE, VehicleState.RETIRED, T0));
  }

  // ------------------------------------------------------------- queries

  @Test
  void history_returnsTheChangesOfTheVehicle() {
    List<VehicleStateChange> changes =
        List.of(
            VehicleStateChange.registration(BIKE_ID, T0),
            new VehicleStateChange(
                BIKE_ID, VehicleState.DEACTIVATED, VehicleState.AVAILABLE, T0.plusSeconds(60)));
    when(vehicles.findById(BIKE_ID)).thenReturn(Optional.of(bikeAtHub03(VehicleState.AVAILABLE)));
    when(history.findByVehicleId(BIKE_ID)).thenReturn(changes);

    assertThat(useCase.history(BIKE_ID)).isEqualTo(changes);
  }

  /** An unknown vehicle is a 404, not an empty history. */
  @Test
  void history_unknownVehicle_isNotFound() {
    when(vehicles.findById(BIKE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.history(BIKE_ID)).isInstanceOf(VehicleNotFoundException.class);
  }

  @Test
  void list_returnsEveryVehicle() {
    List<Vehicle> all = List.of(bikeAtHub03(VehicleState.AVAILABLE));
    when(vehicles.findAll()).thenReturn(all);

    assertThat(useCase.list()).isEqualTo(all);
  }

  @Test
  void get_returnsTheVehicle() {
    when(vehicles.findById(BIKE_ID)).thenReturn(Optional.of(bikeAtHub03(VehicleState.AVAILABLE)));

    assertThat(useCase.get(BIKE_ID)).isEqualTo(bikeAtHub03(VehicleState.AVAILABLE));
  }

  @Test
  void get_unknownVehicle_isNotFound() {
    when(vehicles.findById(BIKE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.get(BIKE_ID)).isInstanceOf(VehicleNotFoundException.class);
  }

  // ------------------------------------------------------------- start of a use (US04)

  /** K3: the booking's check and the change run on the locked vehicle, in one operation. */
  @Test
  void startUse_availableVehicle_isInUseAndTheChangeIsRecorded() {
    when(vehicles.findByIdForUpdate(BIKE_ID))
        .thenReturn(Optional.of(bikeAtHub03(VehicleState.AVAILABLE)));
    when(vehicles.save(any())).then(returnsFirstArg());

    Vehicle vehicle = useCase.startUse(BIKE_ID, locked -> {});

    assertThat(vehicle.state()).isEqualTo(VehicleState.IN_USE);
    assertThat(vehicle.currentHub()).isEmpty();
    verify(history)
        .save(new VehicleStateChange(BIKE_ID, VehicleState.AVAILABLE, VehicleState.IN_USE, T0));
  }

  @Test
  void startUse_checkSeesTheLockedVehicleBeforeTheChange() {
    Vehicle locked = bikeAtHub03(VehicleState.AVAILABLE);
    when(vehicles.findByIdForUpdate(BIKE_ID)).thenReturn(Optional.of(locked));
    when(vehicles.save(any())).then(returnsFirstArg());
    AtomicReference<Vehicle> seen = new AtomicReference<>();

    useCase.startUse(BIKE_ID, seen::set);

    assertThat(seen.get()).isEqualTo(locked);
  }

  /** The booking rules refuse on the locked vehicle: nothing is saved or recorded. */
  @Test
  void startUse_checkRefuses_savesAndRecordsNothing() {
    when(vehicles.findByIdForUpdate(BIKE_ID))
        .thenReturn(Optional.of(bikeAtHub03(VehicleState.AVAILABLE)));
    IllegalStateException refusal = new IllegalStateException("refused by the booking rules");

    assertThatThrownBy(() ->
            useCase.startUse(
                BIKE_ID,
                locked -> {
                  throw refusal;
                }))
        .isSameAs(refusal);
    verify(vehicles, never()).save(any());
    verifyNoInteractions(history);
  }

  // ------------------------------------------------------------- US05: reservations

  @Test
  void reserve_availableVehicle_isReservedAtItsHubAndTheChangeIsRecorded() {
    when(vehicles.findByIdForUpdate(BIKE_ID))
        .thenReturn(Optional.of(bikeAtHub03(VehicleState.AVAILABLE)));
    when(vehicles.save(any())).then(returnsFirstArg());

    Vehicle vehicle = useCase.reserve(BIKE_ID, locked -> {});

    assertThat(vehicle.state()).isEqualTo(VehicleState.RESERVED);
    assertThat(vehicle.currentHub()).isPresent();
    verify(history)
        .save(new VehicleStateChange(BIKE_ID, VehicleState.AVAILABLE, VehicleState.RESERVED, T0));
  }

  @Test
  void startUse_reservedVehicle_isInUseAndTheChangeIsRecorded() {
    when(vehicles.findByIdForUpdate(BIKE_ID))
        .thenReturn(Optional.of(bikeAtHub03(VehicleState.RESERVED)));
    when(vehicles.save(any())).then(returnsFirstArg());

    Vehicle vehicle = useCase.startUse(BIKE_ID, locked -> {});

    assertThat(vehicle.state()).isEqualTo(VehicleState.IN_USE);
    verify(history)
        .save(new VehicleStateChange(BIKE_ID, VehicleState.RESERVED, VehicleState.IN_USE, T0));
  }

  @Test
  void release_reservedVehicle_isAvailableAgainAndTheChangeIsRecorded() {
    when(vehicles.findByIdForUpdate(BIKE_ID))
        .thenReturn(Optional.of(bikeAtHub03(VehicleState.RESERVED)));
    when(vehicles.save(any())).then(returnsFirstArg());

    Vehicle vehicle = useCase.release(BIKE_ID, locked -> {});

    assertThat(vehicle.state()).isEqualTo(VehicleState.AVAILABLE);
    verify(history)
        .save(new VehicleStateChange(BIKE_ID, VehicleState.RESERVED, VehicleState.AVAILABLE, T0));
  }

  /** The booking refuses on the locked vehicle (it was started meanwhile): nothing is saved. */
  @Test
  void release_checkRefuses_savesAndRecordsNothing() {
    when(vehicles.findByIdForUpdate(BIKE_ID))
        .thenReturn(Optional.of(bikeAtHub03(VehicleState.RESERVED)));
    IllegalStateException refusal = new IllegalStateException("no longer overdue");

    assertThatThrownBy(() ->
            useCase.release(
                BIKE_ID,
                locked -> {
                  throw refusal;
                }))
        .isSameAs(refusal);
    verify(vehicles, never()).save(any());
    verifyNoInteractions(history);
  }

  // ------------------------------------------------------------- US06: the end of a ride

  private static Vehicle bikeInUse() {
    return bikeAtHub03(VehicleState.AVAILABLE).startUse();
  }

  @Test
  void endUse_hubWithAFreeSlot_makesTheVehicleAvailableThereAndRecordsIt() {
    when(vehicles.findByIdForUpdate(BIKE_ID)).thenReturn(Optional.of(bikeInUse()));
    when(hubs.findByIdForUpdate(HUB_05_ID)).thenReturn(Optional.of(HUB_05));
    when(vehicles.countAtHub(HUB_05_ID)).thenReturn(0);
    when(vehicles.save(any())).then(returnsFirstArg());

    Vehicle vehicle = useCase.endUse(BIKE_ID, HUB_05_ID, locked -> {});

    assertThat(vehicle.state()).isEqualTo(VehicleState.AVAILABLE);
    assertThat(vehicle.currentHub()).contains(HUB_05_ID);
    verify(history)
        .save(new VehicleStateChange(BIKE_ID, VehicleState.IN_USE, VehicleState.AVAILABLE, T0));
  }

  /**
   * BR18 (KAN-733): a fault reported during the ride left a ticket open, so the vehicle stays out
   * of service at the hub instead of being offered; one change is recorded, InUse to Maintenance.
   */
  @Test
  void endUse_withAnOpenTicket_leavesTheVehicleInMaintenanceThereAndRecordsIt() {
    when(vehicles.findByIdForUpdate(BIKE_ID)).thenReturn(Optional.of(bikeInUse()));
    when(openTickets.existFor(BIKE_ID)).thenReturn(true);
    when(hubs.findByIdForUpdate(HUB_05_ID)).thenReturn(Optional.of(HUB_05));
    when(vehicles.countAtHub(HUB_05_ID)).thenReturn(0);
    when(vehicles.save(any())).then(returnsFirstArg());

    Vehicle vehicle = useCase.endUse(BIKE_ID, HUB_05_ID, locked -> {});

    assertThat(vehicle.state()).isEqualTo(VehicleState.MAINTENANCE);
    assertThat(vehicle.currentHub()).contains(HUB_05_ID);
    verify(history)
        .save(new VehicleStateChange(BIKE_ID, VehicleState.IN_USE, VehicleState.MAINTENANCE, T0));
  }

  /** BR14: the slot is counted with the hub locked, so two ends cannot take the last one. */
  @Test
  void endUse_fullHub_isRefusedAndNothingIsSaved() {
    when(vehicles.findByIdForUpdate(BIKE_ID)).thenReturn(Optional.of(bikeInUse()));
    when(hubs.findByIdForUpdate(HUB_05_ID)).thenReturn(Optional.of(HUB_05));
    when(vehicles.countAtHub(HUB_05_ID)).thenReturn(HUB_05.capacity());

    assertThatThrownBy(() -> useCase.endUse(BIKE_ID, HUB_05_ID, locked -> {}))
        .isInstanceOf(HubFullException.class);
    verify(vehicles, never()).save(any());
    verifyNoInteractions(history);
  }

  /** US05: a Reserved vehicle may be started too, by its booking (Product Specs 3.3). */
  @Test
  void startUse_maintenanceVehicle_isRefusedAndNothingIsSaved() {
    when(vehicles.findByIdForUpdate(BIKE_ID))
        .thenReturn(Optional.of(bikeAtHub03(VehicleState.MAINTENANCE)));

    assertThatThrownBy(() -> useCase.startUse(BIKE_ID, locked -> {}))
        .isInstanceOf(ForbiddenTransitionException.class);
    verify(vehicles, never()).save(any());
    verifyNoInteractions(history);
  }

  @Test
  void startUse_unknownVehicle_isNotFound() {
    when(vehicles.findByIdForUpdate(BIKE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.startUse(BIKE_ID, locked -> {}))
        .isInstanceOf(VehicleNotFoundException.class);
  }
}
