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
 * US10 (KAN-62): maintenance tickets through /tickets and /technicians, as an operator. Who may
 * call each endpoint is checked by AccessMatrixApiIT; here, how the API reports each outcome (400
 * invalid, 404 unknown, 409 conflict with the open tickets or the fleet).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TicketApiIT {

  private static final String PASSWORD = "Passw0rd!";

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private RestClient http;
  private FleetApi operator;
  private UUID operatorId;
  private UUID hub03;

  /** Other tests delete every vehicle and user, so no ticket may point to them afterwards. */
  @AfterEach
  void removeTheTickets() {
    jdbc.update("DELETE FROM maintenance_tickets");
  }

  @BeforeEach
  void loginAsOperatorWithAnEmptyHub() {
    removeTheTickets();
    jdbc.update("DELETE FROM vehicle_state_changes");
    jdbc.update("DELETE FROM vehicles");
    jdbc.update("DELETE FROM hubs");
    jdbc.update("DELETE FROM users");
    http = ApiClient.forPort(port);
    Registered rui = account("Rui Costa", "rui.costa@example.com", "OPERATOR", false);
    operatorId = rui.id();
    operator = new FleetApi(http, rui.token());
    hub03 = operator.hub("hub-03", 10);
  }

  /** Roles and the technician flag are set in the database, as in the other API tests. */
  private Registered account(String name, String email, String role, boolean fieldTechnician) {
    Registered account = ApiClient.registerGuest(http, name, email, PASSWORD);
    jdbc.update(
        "UPDATE users SET role = ?, field_technician = ? WHERE id = ?",
        role,
        fieldTechnician,
        account.id());
    return account;
  }

  private UUID technician(String name, String email) {
    return account(name, email, "OPERATOR", true).id();
  }

  private UUID bikeIn(String code, String action) {
    UUID bike = operator.bike(code, hub03);
    if (action != null) {
      assertThat(operator.transition(bike, action).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    return bike;
  }

  private UUID availableBike() {
    return bikeIn("bike-0009", "ACTIVATE");
  }

  private ResponseEntity<Map<String, Object>> open(UUID vehicleId, String fault, String text) {
    Map<String, Object> body = new HashMap<>();
    body.put("vehicleId", vehicleId);
    body.put("faultCode", fault);
    body.put("description", text);
    return operator.send(HttpMethod.POST, "/tickets", body);
  }

  private UUID openTicket(UUID vehicleId, String fault) {
    return id(FleetApi.created(open(vehicleId, fault, "Seen by the operator")));
  }

  private ResponseEntity<Map<String, Object>> assign(UUID ticketId, UUID technicianId) {
    Map<String, Object> body = new HashMap<>();
    body.put("technicianId", technicianId);
    return operator.send(HttpMethod.PATCH, "/tickets/" + ticketId + "/assignee", body);
  }

  private String vehicleState(UUID vehicleId) {
    return (String) operator.get("/vehicles/" + vehicleId).getBody().get("state");
  }

  // ------------------------------------------------------------- open (BR18, BR19, BR20)

  @Test
  void open_availableVehicle_returns201OpenTicketDueIn24HoursAndTheVehicleIsInMaintenance() {
    UUID bike = availableBike();

    ResponseEntity<Map<String, Object>> response =
        open(bike, "BROKEN_BASKET", "Front basket cracked");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Map<String, Object> ticket = response.getBody();
    assertThat(ticket)
        .containsKey("id")
        .containsEntry("vehicleId", bike.toString())
        .containsEntry("faultCode", "BROKEN_BASKET")
        .containsEntry("description", "Front basket cracked")
        .containsEntry("state", "OPEN")
        .containsEntry("openedBy", operatorId.toString())
        .containsEntry("technicianId", null)
        .containsEntry("automatic", false);
    Instant openedAt = Instant.parse((String) ticket.get("openedAt"));
    Instant deadline = Instant.parse((String) ticket.get("slaDeadline"));
    assertThat(Duration.between(openedAt, deadline)).isEqualTo(Duration.ofHours(24));
    assertThat(vehicleState(bike)).isEqualTo("MAINTENANCE");
  }

  /** BR18: the ride is not interrupted; the ticket waits for the vehicle. */
  @Test
  void open_vehicleInUse_returns201AndTheVehicleStaysInUse() {
    UUID bike = availableBike();
    jdbc.update("UPDATE vehicles SET state = 'IN_USE', hub_id = NULL WHERE id = ?", bike);

    assertThat(open(bike, "BRAKE_WEAR", "Brakes squeak").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(vehicleState(bike)).isEqualTo("IN_USE");
  }

  /** BR20: refused, with the ticket that already covers the fault. */
  @Test
  void open_faultAlreadyCovered_returns409OnTheFaultWithTheOpenTicket() {
    UUID bike = availableBike();
    UUID first = openTicket(bike, "BRAKE_WEAR");

    ResponseEntity<Map<String, Object>> response = open(bike, "BRAKE_WEAR", "Brakes squeak");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(response)).containsOnlyKeys("faultCode");
    assertThat(response.getBody()).containsEntry("ticketId", first.toString());
    assertThat(operator.getList("/tickets").getBody()).hasSize(1);
  }

  @Test
  void open_otherFaultOfTheSameVehicle_returns201() {
    UUID bike = availableBike();
    openTicket(bike, "BRAKE_WEAR");

    assertThat(open(bike, "LOW_TYRE_PRESSURE", "Tyres soft").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void open_invalidFields_returns400NamingEachOne() {
    ResponseEntity<Map<String, Object>> response = open(null, "brake wear", " ");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsOnlyKeys("vehicleId", "faultCode", "description");
  }

  @Test
  void open_descriptionOver500Characters_returns400OnTheDescription() {
    UUID bike = availableBike();

    ResponseEntity<Map<String, Object>> response = open(bike, "BRAKE_WEAR", "x".repeat(501));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsOnlyKeys("description");
  }

  @Test
  void open_unknownVehicle_returns404() {
    ResponseEntity<Map<String, Object>> response =
        open(UUID.randomUUID(), "BRAKE_WEAR", "Brakes squeak");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsKey("errors");
  }

  @Test
  void open_retiredVehicle_returns409OnTheVehicle() {
    UUID bike = bikeIn("bike-0009", "RETIRE");

    ResponseEntity<Map<String, Object>> response = open(bike, "BRAKE_WEAR", "Brakes squeak");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(response)).containsOnlyKeys("vehicleId");
  }

  // ------------------------------------------------------------- list

  /** BR19: the most urgent ticket first. */
  @Test
  void list_returnsTheOpenTicketsNearestDeadlineFirst() {
    UUID first = openTicket(availableBike(), "BRAKE_WEAR");
    UUID second = openTicket(bikeIn("bike-0010", "ACTIVATE"), "LOW_TYRE_PRESSURE");
    jdbc.update(
        "UPDATE maintenance_tickets SET opened_at = opened_at - INTERVAL '2 hours',"
            + " sla_deadline = sla_deadline - INTERVAL '2 hours' WHERE id = ?",
        second);

    List<Map<String, Object>> tickets = operator.getList("/tickets").getBody();

    assertThat(tickets)
        .extracting(ticket -> ticket.get("id"))
        .containsExactly(second.toString(), first.toString());
  }

  // ------------------------------------------------------------- assignment (BR20)

  @Test
  void assign_fieldTechnician_returns200Assigned() {
    UUID ticket = openTicket(availableBike(), "BRAKE_WEAR");
    UUID tiago = technician("Tiago Lopes", "tiago.lopes@example.com");

    ResponseEntity<Map<String, Object>> response = assign(ticket, tiago);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("state", "ASSIGNED")
        .containsEntry("technicianId", tiago.toString());
  }

  @Test
  void assign_operatorWhoIsNotATechnician_returns400OnTheTechnicianAndStaysOpen() {
    UUID ticket = openTicket(availableBike(), "BRAKE_WEAR");
    UUID rita = account("Rita Dias", "rita.dias@example.com", "OPERATOR", false).id();

    ResponseEntity<Map<String, Object>> response = assign(ticket, rita);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsOnlyKeys("technicianId");
    assertThat(operator.getList("/tickets").getBody())
        .singleElement()
        .satisfies(open -> assertThat(open).containsEntry("state", "OPEN"));
  }

  @Test
  void assign_unknownAccount_returns400OnTheTechnician() {
    UUID ticket = openTicket(availableBike(), "BRAKE_WEAR");

    ResponseEntity<Map<String, Object>> response = assign(ticket, UUID.randomUUID());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsOnlyKeys("technicianId");
  }

  @Test
  void assign_withoutTechnician_returns400OnTheTechnician() {
    UUID ticket = openTicket(availableBike(), "BRAKE_WEAR");

    ResponseEntity<Map<String, Object>> response = assign(ticket, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsOnlyKeys("technicianId");
  }

  @Test
  void assign_unknownTicket_returns404() {
    UUID tiago = technician("Tiago Lopes", "tiago.lopes@example.com");

    ResponseEntity<Map<String, Object>> response = assign(UUID.randomUUID(), tiago);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** Once the work has started (US11), the ticket stays with its technician. */
  @Test
  void assign_ticketInProgress_returns409OnTheState() {
    UUID ticket = openTicket(availableBike(), "BRAKE_WEAR");
    UUID tiago = technician("Tiago Lopes", "tiago.lopes@example.com");
    assign(ticket, tiago);
    jdbc.update("UPDATE maintenance_tickets SET state = 'IN_PROGRESS' WHERE id = ?", ticket);
    UUID ana = technician("Ana Matos", "ana.matos@example.com");

    ResponseEntity<Map<String, Object>> response = assign(ticket, ana);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(response)).containsOnlyKeys("state");
  }

  // ------------------------------------------------------------- technicians

  @Test
  void technicians_listsOnlyActiveFieldTechniciansByName() {
    UUID tiago = technician("Tiago Lopes", "tiago.lopes@example.com");
    UUID ana = technician("Ana Matos", "ana.matos@example.com");
    account("Rita Dias", "rita.dias@example.com", "OPERATOR", false);
    UUID bruno = technician("Bruno Reis", "bruno.reis@example.com");
    jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", bruno);

    List<Map<String, Object>> technicians = operator.getList("/technicians").getBody();

    assertThat(technicians)
        .containsExactly(
            Map.of("id", ana.toString(), "name", "Ana Matos"),
            Map.of("id", tiago.toString(), "name", "Tiago Lopes"));
  }
}
