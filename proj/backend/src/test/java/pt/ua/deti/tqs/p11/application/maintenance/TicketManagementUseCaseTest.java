package pt.ua.deti.tqs.p11.application.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.application.DirectTransactionRunner;
import pt.ua.deti.tqs.p11.application.fleet.VehicleNotFoundException;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;
import pt.ua.deti.tqs.p11.domain.maintenance.NotAFieldTechnicianException;
import pt.ua.deti.tqs.p11.domain.maintenance.TicketState;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

/**
 * An operator's work with tickets (US10). What a manual ticket does to the vehicle is tested in
 * OpenFaultTicketUseCaseTest; here, that it is opened on the locked vehicle, and assignment (BR20).
 * The vehicle is only stubbed "for update", so a missing lock fails the tests.
 */
@ExtendWith(MockitoExtension.class)
class TicketManagementUseCaseTest {

  private static final UUID BIKE_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000009");
  private static final UUID HUB_03_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000103");
  private static final UUID TICKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
  private static final UUID OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID TIAGO_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
  private static final FaultCode BRAKE_WEAR = FaultCode.of("BRAKE_WEAR");
  private static final Instant NOW = Instant.parse("2026-11-10T10:00:05Z");
  private static final Duration SLA = Duration.ofHours(24);

  @Mock private TicketRepository tickets;
  @Mock private UserRepository users;
  @Mock private VehicleRepository vehicles;
  @Mock private VehicleStateChangeRepository history;
  @Mock private FaultBookingCancellation bookings;

  private TicketManagementUseCase useCase;

  @BeforeEach
  void setUp() {
    OpenFaultTicketUseCase opener =
        new OpenFaultTicketUseCase(
            tickets,
            vehicles,
            history,
            bookings,
            SLA,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> TICKET_ID);
    useCase =
        new TicketManagementUseCase(
            tickets, users, vehicles, opener, new DirectTransactionRunner());
  }

  private static User account(UUID id, String name, Role role, boolean fieldTechnician) {
    return User.account(
        id, name, new EmailAddress(id + "@example.com"), "hash", role, fieldTechnician);
  }

  private static MaintenanceTicket openTicket() {
    return MaintenanceTicket.openFromFault(
        TICKET_ID, BIKE_ID, BRAKE_WEAR, NOW.minusSeconds(60), SLA);
  }

  // ------------------------------------------------------------- open by hand

  @Test
  void open_knownVehicle_opensTheTicketOnTheLockedVehicle() {
    Vehicle available =
        new Vehicle(
            BIKE_ID,
            FleetCode.of("bike-0009"),
            VehicleType.E_BIKE,
            VehicleState.AVAILABLE,
            HUB_03_ID);
    when(vehicles.findByIdForUpdate(BIKE_ID)).thenReturn(Optional.of(available));

    MaintenanceTicket ticket =
        useCase.open(new OpenTicketCommand(BIKE_ID, "BRAKE_WEAR", "Brakes squeak", OPERATOR_ID));

    assertThat(ticket.vehicleId()).isEqualTo(BIKE_ID);
    assertThat(ticket.faultCode()).isEqualTo(BRAKE_WEAR);
    assertThat(ticket.openedByOperator()).contains(OPERATOR_ID);
    verify(tickets).save(ticket);
    ArgumentCaptor<Vehicle> saved = forClass(Vehicle.class);
    verify(vehicles).save(saved.capture());
    assertThat(saved.getValue().state()).isEqualTo(VehicleState.MAINTENANCE);
  }

  @Test
  void open_unknownVehicle_isRefusedAndOpensNothing() {
    when(vehicles.findByIdForUpdate(BIKE_ID)).thenReturn(Optional.empty());
    OpenTicketCommand command =
        new OpenTicketCommand(BIKE_ID, "BRAKE_WEAR", "Brakes squeak", OPERATOR_ID);

    assertThatThrownBy(() -> useCase.open(command)).isInstanceOf(VehicleNotFoundException.class);
    verify(tickets, never()).save(any());
  }

  // ------------------------------------------------------------- assignment (BR20)

  @Test
  void assign_fieldTechnician_savesTheAssignedTicket() {
    when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(openTicket()));
    when(users.findById(TIAGO_ID))
        .thenReturn(Optional.of(account(TIAGO_ID, "Tiago Lopes", Role.OPERATOR, true)));

    MaintenanceTicket assigned = useCase.assign(TICKET_ID, TIAGO_ID);

    assertThat(assigned.state()).isEqualTo(TicketState.ASSIGNED);
    assertThat(assigned.assignedTechnician()).contains(TIAGO_ID);
    verify(tickets).save(assigned);
  }

  @Test
  void assign_unknownTicket_isRefused() {
    when(tickets.findById(TICKET_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.assign(TICKET_ID, TIAGO_ID))
        .isInstanceOf(TicketNotFoundException.class);
    verify(tickets, never()).save(any());
  }

  /** An unknown account is refused like one that cannot take tickets: no account is revealed. */
  @Test
  void assign_unknownAccount_isRefusedAsNotAFieldTechnician() {
    when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(openTicket()));
    when(users.findById(TIAGO_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.assign(TICKET_ID, TIAGO_ID))
        .isInstanceOfSatisfying(
            NotAFieldTechnicianException.class, e -> assertThat(e.userId()).isEqualTo(TIAGO_ID));
    verify(tickets, never()).save(any());
  }

  @Test
  void assign_operatorWhoIsNotAFieldTechnician_isRefusedAndNothingIsSaved() {
    when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(openTicket()));
    when(users.findById(TIAGO_ID))
        .thenReturn(Optional.of(account(TIAGO_ID, "Tiago Lopes", Role.OPERATOR, false)));

    assertThatThrownBy(() -> useCase.assign(TICKET_ID, TIAGO_ID))
        .isInstanceOf(NotAFieldTechnicianException.class);
    verify(tickets, never()).save(any());
  }

  // ------------------------------------------------------------- who can be assigned

  @Test
  void technicians_areTheActiveFieldTechniciansByName() {
    User tiago = account(TIAGO_ID, "Tiago Lopes", Role.OPERATOR, true);
    User ana = account(UUID.randomUUID(), "Ana Matos", Role.OPERATOR, true);
    when(users.findAll())
        .thenReturn(
            List.of(
                tiago,
                account(UUID.randomUUID(), "Rita Dias", Role.OPERATOR, false),
                account(UUID.randomUUID(), "Bruno Reis", Role.OPERATOR, true).deactivate(),
                ana,
                account(UUID.randomUUID(), "Joana Lima", Role.END_USER, false),
                account(UUID.randomUUID(), "Manuel Sá", Role.CLIENT_MANAGER, false)));

    assertThat(useCase.technicians()).containsExactly(ana, tiago);
  }
}
