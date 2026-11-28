package pt.ua.deti.tqs.p11.application.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.application.DirectTransactionRunner;
import pt.ua.deti.tqs.p11.application.fleet.HubRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleStateChange;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;
import pt.ua.deti.tqs.p11.domain.maintenance.NotTheAssignedTechnicianException;
import pt.ua.deti.tqs.p11.domain.maintenance.TicketState;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

/**
 * A field technician's work (US11). Which states and technicians the ticket accepts is tested in
 * MaintenanceTicketTest; here, the list with the vehicle's location and breach (BR19), and what
 * starting and closing do to the ticket and the vehicle (BR20). The vehicle is only stubbed "for
 * update" in start and close, so a missing lock fails the tests.
 */
@ExtendWith(MockitoExtension.class)
class TechnicianTicketsUseCaseTest {

  private static final UUID BIKE_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000009");
  private static final UUID SCOOTER_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000107");
  private static final UUID HUB_03_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000103");
  private static final UUID BRAKES_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
  private static final UUID TYRE_ID = UUID.fromString("00000000-0000-0000-0000-000000000502");
  private static final UUID TIAGO = UUID.fromString("00000000-0000-0000-0000-000000000301");
  private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-000000000302");
  private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
  private static final Duration SLA = Duration.ofHours(24);
  private static final GeoPosition RIVERSIDE = new GeoPosition(40.6421, -8.6524);
  private static final Hub HUB_03 =
      new Hub(
          HUB_03_ID,
          FleetCode.of("hub-03"),
          "Canal Central",
          new GeoPosition(40.6405, -8.6538),
          10);

  @Mock private TicketRepository tickets;
  @Mock private VehicleRepository vehicles;
  @Mock private HubRepository hubs;
  @Mock private VehicleStateChangeRepository history;

  private TechnicianTicketsUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new TechnicianTicketsUseCase(
            tickets,
            vehicles,
            hubs,
            history,
            new DirectTransactionRunner(),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static User tiago() {
    return User.account(
        TIAGO,
        "Tiago Lopes",
        new EmailAddress("tiago.lopes@example.com"),
        "x",
        Role.OPERATOR,
        true);
  }

  private static Vehicle bikeIn(VehicleState state) {
    return new Vehicle(
        BIKE_ID,
        FleetCode.of("bike-0009"),
        VehicleType.E_BIKE,
        state,
        state.isAtHub() ? HUB_03_ID : null);
  }

  /** Opened 25 hours before now, so its deadline passed an hour ago. */
  private static MaintenanceTicket assignedBrakes() {
    return MaintenanceTicket.openFromFault(
            BRAKES_ID, BIKE_ID, FaultCode.of("BRAKE_WEAR"), NOW.minus(Duration.ofHours(25)), SLA)
        .assignTo(tiago());
  }

  private static MaintenanceTicket brakesInProgress() {
    return assignedBrakes().start(TIAGO);
  }

  private void lockedBikeIs(Vehicle vehicle) {
    when(vehicles.findByIdForUpdate(BIKE_ID)).thenReturn(Optional.of(vehicle));
  }

  private void ticketIs(MaintenanceTicket ticket) {
    when(tickets.findById(ticket.id())).thenReturn(Optional.of(ticket));
  }

  private CloseTicketCommand closeBrakes(UUID technician, String workDone) {
    return new CloseTicketCommand(BRAKES_ID, technician, workDone);
  }

  // ------------------------------------------------------------- the list (BR19)

  @Test
  void myTickets_eachTicketComesWithTheVehicleWhereItIsAndWhetherItIsLate() {
    MaintenanceTicket tyre =
        MaintenanceTicket.openFromFault(
                TYRE_ID, SCOOTER_ID, FaultCode.of("FLAT_TIRE"), NOW.minus(Duration.ofHours(1)), SLA)
            .assignTo(tiago());
    Vehicle scooterRiding =
        new Vehicle(
            SCOOTER_ID,
            FleetCode.of("scooter-0007"),
            VehicleType.E_SCOOTER,
            VehicleState.IN_USE,
            null,
            null,
            null,
            RIVERSIDE,
            NOW.minusSeconds(30));
    when(tickets.findOpenAssignedTo(TIAGO)).thenReturn(List.of(assignedBrakes(), tyre));
    when(vehicles.findById(BIKE_ID)).thenReturn(Optional.of(bikeIn(VehicleState.MAINTENANCE)));
    when(vehicles.findById(SCOOTER_ID)).thenReturn(Optional.of(scooterRiding));
    when(hubs.findById(HUB_03_ID)).thenReturn(Optional.of(HUB_03));

    assertThat(useCase.myTickets(TIAGO))
        .containsExactly(
            new TechnicianTicket(
                assignedBrakes(), "bike-0009", "hub-03", "Canal Central", null, true),
            new TechnicianTicket(tyre, "scooter-0007", null, null, RIVERSIDE, false));
  }

  @Test
  void myTickets_technicianWithoutTickets_isEmpty() {
    when(tickets.findOpenAssignedTo(TIAGO)).thenReturn(List.of());

    assertThat(useCase.myTickets(TIAGO)).isEmpty();
  }

  // ------------------------------------------------------------- starting the work

  @Test
  void start_assignedTicket_savesItInProgressOnTheLockedVehicle() {
    ticketIs(assignedBrakes());
    lockedBikeIs(bikeIn(VehicleState.MAINTENANCE));

    MaintenanceTicket started = useCase.start(BRAKES_ID, TIAGO);

    assertThat(started.state()).isEqualTo(TicketState.IN_PROGRESS);
    verify(tickets).save(started);
  }

  @Test
  void start_byAnotherTechnician_isRefusedAndNothingIsSaved() {
    ticketIs(assignedBrakes());
    lockedBikeIs(bikeIn(VehicleState.MAINTENANCE));

    assertThatThrownBy(() -> useCase.start(BRAKES_ID, ANA))
        .isInstanceOf(NotTheAssignedTechnicianException.class);
    verify(tickets, never()).save(any());
  }

  @Test
  void start_unknownTicket_isRefused() {
    when(tickets.findById(BRAKES_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.start(BRAKES_ID, TIAGO))
        .isInstanceOf(TicketNotFoundException.class);
    verify(tickets, never()).save(any());
  }

  // ------------------------------------------------------------- closing (BR20)

  @Test
  void close_inProgressTicket_isSavedClosedNowWithTheWorkDone() {
    ticketIs(brakesInProgress());
    lockedBikeIs(bikeIn(VehicleState.MAINTENANCE));
    when(tickets.existsOpenFor(BIKE_ID)).thenReturn(true);

    MaintenanceTicket closed = useCase.close(closeBrakes(TIAGO, "Replaced brake pads"));

    assertThat(closed.state()).isEqualTo(TicketState.CLOSED);
    assertThat(closed.closedAt()).isEqualTo(NOW);
    assertThat(closed.workDone()).isEqualTo("Replaced brake pads");
    verify(tickets).save(closed);
  }

  @Test
  void close_lastOpenTicketOfAVehicleInMaintenance_returnsItToServiceAndRecordsTheChange() {
    ticketIs(brakesInProgress());
    lockedBikeIs(bikeIn(VehicleState.MAINTENANCE));
    when(tickets.existsOpenFor(BIKE_ID)).thenReturn(false);

    useCase.close(closeBrakes(TIAGO, "Replaced brake pads"));

    ArgumentCaptor<Vehicle> saved = forClass(Vehicle.class);
    verify(vehicles).save(saved.capture());
    assertThat(saved.getValue().state()).isEqualTo(VehicleState.AVAILABLE);
    assertThat(saved.getValue().currentHub()).contains(HUB_03_ID);
    verify(history)
        .save(
            new VehicleStateChange(BIKE_ID, VehicleState.MAINTENANCE, VehicleState.AVAILABLE, NOW));
  }

  @Test
  void close_whileAnotherTicketIsOpen_keepsTheVehicleInMaintenance() {
    ticketIs(brakesInProgress());
    lockedBikeIs(bikeIn(VehicleState.MAINTENANCE));
    when(tickets.existsOpenFor(BIKE_ID)).thenReturn(true);

    useCase.close(closeBrakes(TIAGO, "Replaced brake pads"));

    verify(vehicles, never()).save(any());
    verifyNoInteractions(history);
  }

  /** A ride is not interrupted, and a vehicle out of service stays as it is. */
  @ParameterizedTest
  @EnumSource(names = {"IN_USE", "DEACTIVATED"})
  void close_vehicleNotInMaintenance_keepsItsState(VehicleState state) {
    ticketIs(brakesInProgress());
    lockedBikeIs(bikeIn(state));

    useCase.close(closeBrakes(TIAGO, "Replaced brake pads"));

    verify(vehicles, never()).save(any());
    verifyNoInteractions(history);
  }

  @Test
  void close_blankWorkDone_isRefusedAndNothingIsSaved() {
    ticketIs(brakesInProgress());
    lockedBikeIs(bikeIn(VehicleState.MAINTENANCE));
    CloseTicketCommand command = closeBrakes(TIAGO, "   ");

    assertThatThrownBy(() -> useCase.close(command)).isInstanceOf(IllegalArgumentException.class);
    verify(tickets, never()).save(any());
    verify(vehicles, never()).save(any());
  }

  @Test
  void close_byAnotherTechnician_isRefusedAndNothingIsSaved() {
    ticketIs(brakesInProgress());
    lockedBikeIs(bikeIn(VehicleState.MAINTENANCE));
    CloseTicketCommand command = closeBrakes(ANA, "Replaced brake pads");

    assertThatThrownBy(() -> useCase.close(command))
        .isInstanceOf(NotTheAssignedTechnicianException.class);
    verify(tickets, never()).save(any());
    verify(vehicles, never()).save(any());
  }

  @Test
  void close_unknownTicket_isRefused() {
    when(tickets.findById(BRAKES_ID)).thenReturn(Optional.empty());
    CloseTicketCommand command = closeBrakes(TIAGO, "Replaced brake pads");

    assertThatThrownBy(() -> useCase.close(command)).isInstanceOf(TicketNotFoundException.class);
    verifyNoInteractions(vehicles, history);
  }
}
