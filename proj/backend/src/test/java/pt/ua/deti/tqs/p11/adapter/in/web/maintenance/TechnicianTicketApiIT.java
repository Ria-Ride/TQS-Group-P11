package pt.ua.deti.tqs.p11.adapter.in.web.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.ua.deti.tqs.p11.support.FleetApi.errors;
import static pt.ua.deti.tqs.p11.support.FleetApi.id;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;
import pt.ua.deti.tqs.p11.support.FleetApi;

/**
 * US11 (KAN-63): a field technician's tickets through /tickets/mine, /tickets/{id}/start and
 * /tickets/{id}/close. Who may call each endpoint is checked by AccessMatrixApiIT; here, how the
 * API reports each outcome (400 invalid, 403 not their ticket, 404 unknown, 409 wrong state).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TechnicianTicketApiIT {

  private static final String PASSWORD = "Passw0rd!";

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private RestClient http;
  private FleetApi operator;
  private FleetApi tiago;
  private FleetApi ana;
  private UUID tiagoId;
  private UUID anaId;
  private UUID hub03;

  /** Other tests delete every vehicle and user, so no ticket may point to them afterwards. */
  @AfterEach
  void removeTheTickets() {
    jdbc.update("DELETE FROM maintenance_tickets");
  }

  @BeforeEach
  void anOperatorTwoTechniciansAndAHub() {
    removeTheTickets();
    jdbc.update("DELETE FROM vehicle_state_changes");
    jdbc.update("DELETE FROM vehicles");
    jdbc.update("DELETE FROM hubs");
    jdbc.update("DELETE FROM users");
    http = ApiClient.forPort(port);
    operator = new FleetApi(http, account("Rui Costa", "rui.costa@example.com", false).token());
    Registered tiagoAccount = account("Tiago Lopes", "tiago.lopes@example.com", true);
    tiagoId = tiagoAccount.id();
    tiago = new FleetApi(http, tiagoAccount.token());
    Registered anaAccount = account("Ana Matos", "ana.matos@example.com", true);
    anaId = anaAccount.id();
    ana = new FleetApi(http, anaAccount.token());
    hub03 = operator.hub("hub-03", 10);
  }

  private Registered account(String name, String email, boolean fieldTechnician) {
    Registered account = ApiClient.registerGuest(http, name, email, PASSWORD);
    jdbc.update(
        "UPDATE users SET role = 'OPERATOR', field_technician = ? WHERE id = ?",
        fieldTechnician,
        account.id());
    return account;
  }

  private UUID availableBike(String code) {
    UUID bike = operator.bike(code, hub03);
    assertThat(operator.transition(bike, "ACTIVATE").getStatusCode()).isEqualTo(HttpStatus.OK);
    return bike;
  }

  /** A manual ticket, so the bike goes to Maintenance, assigned to the technician. */
  private UUID ticketAssignedTo(UUID bike, String fault, UUID technicianId) {
    Map<String, Object> body = new HashMap<>();
    body.put("vehicleId", bike);
    body.put("faultCode", fault);
    body.put("description", "Seen by the operator");
    UUID ticket = id(FleetApi.created(operator.send(HttpMethod.POST, "/tickets", body)));
    assertThat(
            operator
                .send(
                    HttpMethod.PATCH,
                    "/tickets/" + ticket + "/assignee",
                    Map.of("technicianId", technicianId))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    return ticket;
  }

  private void openedHoursAgo(UUID ticket, int hours) {
    jdbc.update(
        "UPDATE maintenance_tickets SET opened_at = opened_at - make_interval(hours => ?),"
            + " sla_deadline = sla_deadline - make_interval(hours => ?) WHERE id = ?",
        hours,
        hours,
        ticket);
  }

  private ResponseEntity<Map<String, Object>> start(FleetApi technician, UUID ticket) {
    return technician.send(HttpMethod.POST, "/tickets/" + ticket + "/start", Map.of());
  }

  private ResponseEntity<Map<String, Object>> close(FleetApi technician, UUID ticket, String work) {
    Map<String, Object> body = new HashMap<>();
    body.put("workDone", work);
    return technician.send(HttpMethod.POST, "/tickets/" + ticket + "/close", body);
  }

  private String vehicleState(UUID vehicle) {
    return (String) operator.get("/vehicles/" + vehicle).getBody().get("state");
  }

  private String ticketState(UUID ticket) {
    return jdbc.queryForObject(
        "SELECT state FROM maintenance_tickets WHERE id = ?", String.class, ticket);
  }

  // ------------------------------------------------------------- my tickets (BR19)

  @Test
  void mine_listsOnlyMyOpenTicketsNearestDeadlineFirst_withTheVehicleAndWhereItIs() {
    UUID bike9 = availableBike("bike-0009");
    UUID bike10 = availableBike("bike-0010");
    UUID later = ticketAssignedTo(bike9, "BRAKE_WEAR", tiagoId);
    UUID late = ticketAssignedTo(bike10, "FLAT_TIRE", tiagoId);
    ticketAssignedTo(bike10, "BATTERY_OVERHEAT", anaId);
    openedHoursAgo(late, 25);

    List<Map<String, Object>> mine = tiago.getList("/tickets/mine").getBody();

    assertThat(mine)
        .extracting(ticket -> ticket.get("id"))
        .containsExactly(late.toString(), later.toString());
    assertThat(mine.get(0))
        .containsEntry("vehicleId", bike10.toString())
        .containsEntry("vehicleCode", "bike-0010")
        .containsEntry("faultCode", "FLAT_TIRE")
        .containsEntry("description", "Seen by the operator")
        .containsEntry("state", "ASSIGNED")
        .containsEntry("hubCode", "hub-03")
        .containsEntry("hubName", "Hub hub-03")
        .containsEntry("breached", true)
        .containsKeys("openedAt", "slaDeadline", "latitude", "longitude");
    assertThat(mine.get(1)).containsEntry("breached", false);
  }

  @Test
  void mine_withoutTickets_returnsAnEmptyList() {
    ResponseEntity<List<Map<String, Object>>> response = tiago.getList("/tickets/mine");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEmpty();
  }

  // ------------------------------------------------------------- start

  @Test
  void start_myAssignedTicket_returns200InProgress() {
    UUID ticket = ticketAssignedTo(availableBike("bike-0009"), "BRAKE_WEAR", tiagoId);

    ResponseEntity<Map<String, Object>> response = start(tiago, ticket);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("state", "IN_PROGRESS")
        .containsEntry("technicianId", tiagoId.toString());
  }

  @Test
  void start_someoneElsesTicket_returns403AndChangesNothing() {
    UUID ticket = ticketAssignedTo(availableBike("bike-0009"), "BRAKE_WEAR", tiagoId);

    assertThat(start(ana, ticket).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(ticketState(ticket)).isEqualTo("ASSIGNED");
  }

  @Test
  void start_unknownTicket_returns404() {
    assertThat(start(tiago, UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void start_ticketAlreadyInProgress_returns409OnTheState() {
    UUID ticket = ticketAssignedTo(availableBike("bike-0009"), "BRAKE_WEAR", tiagoId);
    start(tiago, ticket);

    ResponseEntity<Map<String, Object>> response = start(tiago, ticket);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(response)).containsOnlyKeys("state");
  }

  // ------------------------------------------------------------- close (BR19, BR20)

  @Test
  void close_myInProgressTicket_returns200ClosedWithTheWorkAndTheTimeToRepair() {
    UUID bike = availableBike("bike-0009");
    UUID ticket = ticketAssignedTo(bike, "BRAKE_WEAR", tiagoId);
    start(tiago, ticket);
    openedHoursAgo(ticket, 23);

    ResponseEntity<Map<String, Object>> response = close(tiago, ticket, "Replaced brake pads");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> closed = response.getBody();
    assertThat(closed)
        .containsEntry("state", "CLOSED")
        .containsEntry("workDone", "Replaced brake pads")
        .containsEntry("breached", false);
    Instant openedAt = Instant.parse((String) closed.get("openedAt"));
    Instant closedAt = Instant.parse((String) closed.get("closedAt"));
    assertThat(((Number) closed.get("timeToRepairMinutes")).longValue())
        .isEqualTo(Duration.between(openedAt, closedAt).toMinutes())
        .isBetween(23L * 60, 23L * 60 + 5);
  }

  /** BR19: a ticket closed after its deadline stays breached for the KPIs. */
  @Test
  void close_afterTheDeadline_returnsItBreached() {
    UUID ticket = ticketAssignedTo(availableBike("bike-0009"), "BRAKE_WEAR", tiagoId);
    start(tiago, ticket);
    openedHoursAgo(ticket, 25);

    assertThat(close(tiago, ticket, "Replaced brake pads").getBody())
        .containsEntry("breached", true);
  }

  /** BR20: the vehicle had no other open ticket, so it is back in service. */
  @Test
  void close_lastOpenTicket_returnsTheVehicleToAvailable() {
    UUID bike = availableBike("bike-0009");
    UUID ticket = ticketAssignedTo(bike, "BRAKE_WEAR", tiagoId);
    start(tiago, ticket);
    assertThat(vehicleState(bike)).isEqualTo("MAINTENANCE");

    close(tiago, ticket, "Replaced brake pads");

    assertThat(vehicleState(bike)).isEqualTo("AVAILABLE");
  }

  @Test
  void close_whileAnotherTicketIsOpen_keepsTheVehicleInMaintenance() {
    UUID bike = availableBike("bike-0009");
    UUID ticket = ticketAssignedTo(bike, "BRAKE_WEAR", tiagoId);
    ticketAssignedTo(bike, "FLAT_TIRE", anaId);
    start(tiago, ticket);

    close(tiago, ticket, "Replaced brake pads");

    assertThat(vehicleState(bike)).isEqualTo("MAINTENANCE");
  }

  @Test
  void close_blankWorkDone_returns400OnTheWorkAndStaysInProgress() {
    UUID ticket = ticketAssignedTo(availableBike("bike-0009"), "BRAKE_WEAR", tiagoId);
    start(tiago, ticket);

    ResponseEntity<Map<String, Object>> response = close(tiago, ticket, "   ");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsOnlyKeys("workDone");
    assertThat(ticketState(ticket)).isEqualTo("IN_PROGRESS");
  }

  @Test
  void close_workDoneOver1000Characters_returns400OnTheWork() {
    UUID ticket = ticketAssignedTo(availableBike("bike-0009"), "BRAKE_WEAR", tiagoId);
    start(tiago, ticket);

    ResponseEntity<Map<String, Object>> response = close(tiago, ticket, "x".repeat(1001));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsOnlyKeys("workDone");
  }

  @Test
  void close_ticketNotStarted_returns409OnTheState() {
    UUID ticket = ticketAssignedTo(availableBike("bike-0009"), "BRAKE_WEAR", tiagoId);

    ResponseEntity<Map<String, Object>> response = close(tiago, ticket, "Replaced brake pads");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(response)).containsOnlyKeys("state");
    assertThat(ticketState(ticket)).isEqualTo("ASSIGNED");
  }

  @Test
  void close_someoneElsesTicket_returns403AndChangesNothing() {
    UUID bike = availableBike("bike-0009");
    UUID ticket = ticketAssignedTo(bike, "BRAKE_WEAR", tiagoId);
    start(tiago, ticket);

    assertThat(close(ana, ticket, "Replaced brake pads").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(ticketState(ticket)).isEqualTo("IN_PROGRESS");
    assertThat(vehicleState(bike)).isEqualTo("MAINTENANCE");
  }

  @Test
  void close_unknownTicket_returns404() {
    assertThat(close(tiago, UUID.randomUUID(), "Replaced brake pads").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
