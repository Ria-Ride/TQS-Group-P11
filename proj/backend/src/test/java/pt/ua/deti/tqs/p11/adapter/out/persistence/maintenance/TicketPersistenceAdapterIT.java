package pt.ua.deti.tqs.p11.adapter.out.persistence.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.application.fleet.HubRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.maintenance.FaultAlreadyCoveredException;
import pt.ua.deti.tqs.p11.application.maintenance.TicketRepository;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;
import pt.ua.deti.tqs.p11.domain.maintenance.TicketState;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

/**
 * Maintenance tickets in PostgreSQL (US10). BR20 is enforced by the database too: at most one open
 * ticket per vehicle and fault, so two readings of the same fault stored together cannot both open
 * one.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TicketPersistenceAdapterIT {

  private static final UUID HUB_ID = UUID.fromString("5e6f7a8b-0000-4000-8000-000000000001");
  private static final UUID VEHICLE_ID = UUID.fromString("5e6f7a8b-0000-4000-8000-000000000009");
  private static final UUID OPERATOR_ID = UUID.fromString("5e6f7a8b-0000-4000-8000-000000000201");
  private static final UUID TECHNICIAN_ID = UUID.fromString("5e6f7a8b-0000-4000-8000-000000000301");
  private static final String EMAILS = "'ticket.%.it@example.com'";

  private static final FaultCode BRAKE_WEAR = FaultCode.of("BRAKE_WEAR");
  private static final FaultCode LOW_TYRE_PRESSURE = FaultCode.of("LOW_TYRE_PRESSURE");
  private static final Instant OPENED_AT = Instant.parse("2026-11-10T10:00:00Z");
  private static final Duration SLA = Duration.ofHours(24);

  @Autowired private TicketRepository tickets;
  @Autowired private VehicleRepository vehicles;
  @Autowired private HubRepository hubs;
  @Autowired private JdbcTemplate jdbc;

  private static MaintenanceTicket fromFault(FaultCode fault) {
    return MaintenanceTicket.openFromFault(UUID.randomUUID(), VEHICLE_ID, fault, OPENED_AT, SLA);
  }

  private static MaintenanceTicket finished(TicketState state, FaultCode fault) {
    return new MaintenanceTicket(
        UUID.randomUUID(),
        VEHICLE_ID,
        fault,
        null,
        state,
        OPENED_AT,
        OPENED_AT.plus(SLA),
        null,
        TECHNICIAN_ID);
  }

  private static User technician() {
    return User.account(
        TECHNICIAN_ID,
        "Tiago Lopes",
        new EmailAddress("ticket.tech.it@example.com"),
        "x",
        Role.OPERATOR,
        true);
  }

  private int rowsOfTheVehicle() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM maintenance_tickets WHERE vehicle_id = ?", Integer.class, VEHICLE_ID);
  }

  /**
   * Before and after each test: other tests delete every vehicle, hub and user, so no ticket may
   * point to them when this test ends.
   */
  @AfterEach
  void removeTheRowsOfThisTest() {
    jdbc.update("DELETE FROM maintenance_tickets WHERE vehicle_id = ?", VEHICLE_ID);
    jdbc.update("DELETE FROM vehicle_state_changes WHERE vehicle_id = ?", VEHICLE_ID);
    jdbc.update("DELETE FROM vehicles WHERE id = ?", VEHICLE_ID);
    jdbc.update("DELETE FROM hubs WHERE id = ?", HUB_ID);
    jdbc.update("DELETE FROM users WHERE email LIKE " + EMAILS);
  }

  @BeforeEach
  void aVehicleAnOperatorAndATechnician() {
    removeTheRowsOfThisTest();
    hubs.save(
        new Hub(
            HUB_ID,
            FleetCode.of("ticket-it-hub"),
            "Ticket Test Hub",
            new GeoPosition(40.6405, -8.6538),
            10));
    vehicles.save(
        Vehicle.register(VEHICLE_ID, FleetCode.of("ticket-it-bike"), VehicleType.E_BIKE, HUB_ID));
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, role)"
            + " VALUES (?, 'Sofia Operator', 'ticket.operator.it@example.com', 'x', 'OPERATOR')",
        OPERATOR_ID);
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, role, field_technician)"
            + " VALUES (?, 'Tiago Lopes', 'ticket.tech.it@example.com', 'x', 'OPERATOR', TRUE)",
        TECHNICIAN_ID);
  }

  // ------------------------------------------------------------- save and read

  @Test
  void save_ticketFromFault_storesEveryField() {
    MaintenanceTicket ticket = fromFault(BRAKE_WEAR);

    tickets.save(ticket);

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT vehicle_id, fault_code, description, state, opened_by, technician_id"
                + " FROM maintenance_tickets WHERE id = ?",
            ticket.id());
    assertThat(row)
        .containsEntry("vehicle_id", VEHICLE_ID)
        .containsEntry("fault_code", "BRAKE_WEAR")
        .containsEntry("description", null)
        .containsEntry("state", "OPEN")
        .containsEntry("opened_by", null)
        .containsEntry("technician_id", null);
  }

  @Test
  void findById_returnsTheStoredTicketWithTheSameDeadline() {
    MaintenanceTicket ticket = fromFault(BRAKE_WEAR);
    tickets.save(ticket);

    assertThat(tickets.findById(ticket.id())).contains(ticket);
  }

  @Test
  void findById_manualAssignedTicket_keepsOperatorDescriptionAndTechnician() {
    MaintenanceTicket ticket =
        MaintenanceTicket.openManually(
                UUID.randomUUID(),
                VEHICLE_ID,
                FaultCode.of("BROKEN_BASKET"),
                "Front basket cracked",
                OPERATOR_ID,
                OPENED_AT,
                SLA)
            .assignTo(technician());
    tickets.save(ticket);

    assertThat(tickets.findById(ticket.id())).contains(ticket);
  }

  @Test
  void findById_unknownTicket_isEmpty() {
    assertThat(tickets.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void save_existingTicket_updatesItInPlace() {
    MaintenanceTicket open = fromFault(BRAKE_WEAR);
    tickets.save(open);

    tickets.save(open.assignTo(technician()));

    assertThat(rowsOfTheVehicle()).isEqualTo(1);
    assertThat(tickets.findById(open.id()))
        .hasValueSatisfying(stored -> {
          assertThat(stored.state()).isEqualTo(TicketState.ASSIGNED);
          assertThat(stored.assignedTechnician()).contains(TECHNICIAN_ID);
        });
  }

  // ------------------------------------------------------------- BR20: one open ticket per fault

  @Test
  void save_secondOpenTicketForTheSameFault_isRefused() {
    tickets.save(fromFault(BRAKE_WEAR));
    MaintenanceTicket duplicate = fromFault(BRAKE_WEAR);

    assertThatThrownBy(() -> tickets.save(duplicate))
        .isInstanceOfSatisfying(
            FaultAlreadyCoveredException.class,
            e -> {
              assertThat(e.vehicleId()).isEqualTo(VEHICLE_ID);
              assertThat(e.faultCode()).isEqualTo(BRAKE_WEAR);
            });
    assertThat(rowsOfTheVehicle()).isEqualTo(1);
  }

  /** An assigned ticket is still open, so it still covers its fault. */
  @Test
  void save_sameFaultWhileTheFirstIsAssigned_isRefused() {
    tickets.save(fromFault(BRAKE_WEAR).assignTo(technician()));
    MaintenanceTicket duplicate = fromFault(BRAKE_WEAR);

    assertThatThrownBy(() -> tickets.save(duplicate))
        .isInstanceOf(FaultAlreadyCoveredException.class);
  }

  @Test
  void save_differentFaultOnTheSameVehicle_isAccepted() {
    tickets.save(fromFault(BRAKE_WEAR));

    tickets.save(fromFault(LOW_TYRE_PRESSURE));

    assertThat(rowsOfTheVehicle()).isEqualTo(2);
  }

  /** A fault that comes back after its ticket is finished opens a new ticket. */
  @Test
  void save_sameFaultAfterTheFirstIsFinished_isAccepted() {
    tickets.save(finished(TicketState.CLOSED, BRAKE_WEAR));
    tickets.save(finished(TicketState.CANCELLED, BRAKE_WEAR));

    tickets.save(fromFault(BRAKE_WEAR));

    assertThat(rowsOfTheVehicle()).isEqualTo(3);
  }

  // ------------------------------------------------------------- open tickets

  @Test
  void findOpenFor_returnsTheOpenTicketOfThatFaultOnly() {
    MaintenanceTicket brakes = fromFault(BRAKE_WEAR);
    tickets.save(brakes);
    tickets.save(fromFault(LOW_TYRE_PRESSURE));

    assertThat(tickets.findOpenFor(VEHICLE_ID, BRAKE_WEAR)).contains(brakes);
  }

  @Test
  void findOpenFor_onlyFinishedTicketsOfThatFault_isEmpty() {
    tickets.save(finished(TicketState.CLOSED, BRAKE_WEAR));
    tickets.save(finished(TicketState.CANCELLED, BRAKE_WEAR));

    assertThat(tickets.findOpenFor(VEHICLE_ID, BRAKE_WEAR)).isEmpty();
  }

  @Test
  void findOpenFor_otherVehicle_isEmpty() {
    tickets.save(fromFault(BRAKE_WEAR));

    assertThat(tickets.findOpenFor(UUID.randomUUID(), BRAKE_WEAR)).isEmpty();
  }

  /** BR19: the operator sees the most urgent ticket first; finished ones are not listed. */
  @Test
  void findOpen_listsOnlyOpenTicketsByDeadline() {
    MaintenanceTicket later = fromFault(BRAKE_WEAR);
    MaintenanceTicket sooner =
        MaintenanceTicket.openFromFault(
                UUID.randomUUID(), VEHICLE_ID, LOW_TYRE_PRESSURE, OPENED_AT.minusSeconds(3600), SLA)
            .assignTo(technician());
    tickets.save(later);
    tickets.save(sooner);
    tickets.save(finished(TicketState.CLOSED, FaultCode.of("FLAT_TYRE")));

    assertThat(tickets.findOpen())
        .filteredOn(ticket -> ticket.vehicleId().equals(VEHICLE_ID))
        .containsExactly(sooner, later);
  }

  @Test
  void existsOpenFor_vehicleWithAnAssignedTicket_isTrue() {
    tickets.save(fromFault(BRAKE_WEAR).assignTo(technician()));

    assertThat(tickets.existsOpenFor(VEHICLE_ID)).isTrue();
  }

  @Test
  void existsOpenFor_vehicleWithoutTickets_isFalse() {
    assertThat(tickets.existsOpenFor(VEHICLE_ID)).isFalse();
  }

  @Test
  void existsOpenFor_vehicleWithOnlyFinishedTickets_isFalse() {
    tickets.save(finished(TicketState.CLOSED, BRAKE_WEAR));
    tickets.save(finished(TicketState.CANCELLED, LOW_TYRE_PRESSURE));

    assertThat(tickets.existsOpenFor(VEHICLE_ID)).isFalse();
  }

  // ------------------------------------------------------------- rows edited by hand

  private void insertRow(String state, Instant deadline, UUID technicianId) {
    jdbc.update(
        "INSERT INTO maintenance_tickets"
            + " (id, vehicle_id, fault_code, state, opened_at, sla_deadline, technician_id)"
            + " VALUES (?, ?, 'BRAKE_WEAR', ?, ?, ?, ?)",
        UUID.randomUUID(),
        VEHICLE_ID,
        state,
        Timestamp.from(OPENED_AT),
        Timestamp.from(deadline),
        technicianId);
  }

  @Test
  void database_refusesADeadlineNotAfterTheOpening() {
    assertThatThrownBy(() -> insertRow("OPEN", OPENED_AT, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void database_refusesAnOpenTicketWithATechnician() {
    Instant deadline = OPENED_AT.plus(SLA);

    assertThatThrownBy(() -> insertRow("OPEN", deadline, TECHNICIAN_ID))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void database_refusesAnAssignedTicketWithoutATechnician() {
    Instant deadline = OPENED_AT.plus(SLA);

    assertThatThrownBy(() -> insertRow("ASSIGNED", deadline, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void database_refusesAnUnknownState() {
    Instant deadline = OPENED_AT.plus(SLA);

    assertThatThrownBy(() -> insertRow("WAITING", deadline, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
