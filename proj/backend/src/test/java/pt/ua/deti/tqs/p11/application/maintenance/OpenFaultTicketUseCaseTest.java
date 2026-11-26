package pt.ua.deti.tqs.p11.application.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
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
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleStateChange;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;
import pt.ua.deti.tqs.p11.domain.maintenance.TicketState;

/**
 * Opening a ticket from a fault reading (US10). The vehicle comes already locked, inside the
 * transaction of the reading, so this use case only decides and saves. BR18: Available and Reserved
 * go to Maintenance; BR19: the deadline is now plus the SLA; BR20: one open ticket per fault.
 */
@ExtendWith(MockitoExtension.class)
class OpenFaultTicketUseCaseTest {

  private static final UUID BIKE_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000009");
  private static final UUID HUB_03_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000103");
  private static final UUID TICKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
  private static final FleetCode BIKE_CODE = FleetCode.of("bike-0009");
  private static final FaultCode BRAKE_WEAR = FaultCode.of("BRAKE_WEAR");
  private static final Instant NOW = Instant.parse("2026-11-10T10:00:05Z");
  private static final Duration SLA = Duration.ofHours(24);

  @Mock private TicketRepository tickets;
  @Mock private VehicleRepository vehicles;
  @Mock private VehicleStateChangeRepository history;
  @Mock private FaultBookingCancellation bookings;

  private OpenFaultTicketUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new OpenFaultTicketUseCase(
            tickets,
            vehicles,
            history,
            bookings,
            SLA,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> TICKET_ID);
  }

  private static Vehicle bikeIn(VehicleState state) {
    return new Vehicle(
        BIKE_ID, BIKE_CODE, VehicleType.E_BIKE, state, state.isAtHub() ? HUB_03_ID : null);
  }

  private MaintenanceTicket savedTicket() {
    ArgumentCaptor<MaintenanceTicket> saved = forClass(MaintenanceTicket.class);
    verify(tickets).save(saved.capture());
    return saved.getValue();
  }

  // ------------------------------------------------------------- the ticket

  /** BR19: opened now, due now + 24 h, Open and with no operator (it came from telemetry). */
  @Test
  void faultReported_newFault_opensATicketDueNowPlusTheSla() {
    useCase.faultReported(bikeIn(VehicleState.AVAILABLE), BRAKE_WEAR);

    MaintenanceTicket ticket = savedTicket();
    assertThat(ticket.id()).isEqualTo(TICKET_ID);
    assertThat(ticket.vehicleId()).isEqualTo(BIKE_ID);
    assertThat(ticket.faultCode()).isEqualTo(BRAKE_WEAR);
    assertThat(ticket.state()).isEqualTo(TicketState.OPEN);
    assertThat(ticket.isAutomatic()).isTrue();
    assertThat(ticket.openedAt()).isEqualTo(NOW);
    assertThat(ticket.slaDeadline()).isEqualTo(Instant.parse("2026-11-11T10:00:05Z"));
  }

  /** BR20: the simulator repeats the fault in every reading; only the first one opens a ticket. */
  @Test
  void faultReported_faultAlreadyWithAnOpenTicket_changesNothing() {
    MaintenanceTicket open =
        MaintenanceTicket.openFromFault(
            UUID.randomUUID(), BIKE_ID, BRAKE_WEAR, NOW.minusSeconds(60), SLA);
    when(tickets.findOpenFor(BIKE_ID, BRAKE_WEAR)).thenReturn(Optional.of(open));

    useCase.faultReported(bikeIn(VehicleState.MAINTENANCE), BRAKE_WEAR);

    verify(tickets, never()).save(any());
    verifyNoInteractions(vehicles, history, bookings);
  }

  /** BR20: another fault of a vehicle already in Maintenance opens its own ticket. */
  @Test
  void faultReported_otherFaultOfAVehicleInMaintenance_opensASecondTicket() {
    FaultCode tyres = FaultCode.of("LOW_TYRE_PRESSURE");
    when(tickets.findOpenFor(BIKE_ID, tyres)).thenReturn(Optional.empty());

    useCase.faultReported(bikeIn(VehicleState.MAINTENANCE), tyres);

    assertThat(savedTicket().faultCode()).isEqualTo(tyres);
    verifyNoInteractions(vehicles, history);
  }

  // ------------------------------------------------------------- the vehicle

  @Test
  void faultReported_availableVehicle_goesToMaintenanceAndTheChangeIsRecorded() {
    Vehicle available = bikeIn(VehicleState.AVAILABLE);

    useCase.faultReported(available, BRAKE_WEAR);

    ArgumentCaptor<Vehicle> saved = forClass(Vehicle.class);
    verify(vehicles).save(saved.capture());
    assertThat(saved.getValue().state()).isEqualTo(VehicleState.MAINTENANCE);
    assertThat(saved.getValue().currentHub()).contains(HUB_03_ID);
    verify(history)
        .save(
            new VehicleStateChange(BIKE_ID, VehicleState.AVAILABLE, VehicleState.MAINTENANCE, NOW));
    verifyNoInteractions(bookings);
  }

  /** BR18: the booking is cancelled with reason "vehicle fault" and its deposit released. */
  @Test
  void faultReported_reservedVehicle_goesToMaintenanceAndItsBookingIsCancelled() {
    useCase.faultReported(bikeIn(VehicleState.RESERVED), BRAKE_WEAR);

    ArgumentCaptor<Vehicle> saved = forClass(Vehicle.class);
    verify(vehicles).save(saved.capture());
    assertThat(saved.getValue().state()).isEqualTo(VehicleState.MAINTENANCE);
    verify(history)
        .save(
            new VehicleStateChange(BIKE_ID, VehicleState.RESERVED, VehicleState.MAINTENANCE, NOW));
    verify(bookings).cancelReservedBookingOf(BIKE_ID);
  }

  /**
   * BR18: an InUse ride is not interrupted, and a vehicle that is not offered stays as it is; the
   * ticket is opened all the same, so it is repaired before it is offered again.
   */
  @ParameterizedTest
  @EnumSource(names = {"IN_USE", "DEACTIVATED", "IN_TRANSIT"})
  void faultReported_vehicleNotOffered_opensATicketAndKeepsItsState(VehicleState state) {
    useCase.faultReported(bikeIn(state), BRAKE_WEAR);

    assertThat(savedTicket().vehicleId()).isEqualTo(BIKE_ID);
    verifyNoInteractions(vehicles, history, bookings);
  }

  /** Retirement is final: a ticket for a Retired vehicle could never be closed. */
  @Test
  void faultReported_retiredVehicle_isIgnored() {
    useCase.faultReported(bikeIn(VehicleState.RETIRED), BRAKE_WEAR);

    verifyNoInteractions(tickets, vehicles, history, bookings);
  }
}
