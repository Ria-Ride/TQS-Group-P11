package pt.ua.deti.tqs.p11.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static pt.ua.deti.tqs.p11.support.FleetApi.errors;
import static pt.ua.deti.tqs.p11.support.TelemetryMessages.FLEET;
import static pt.ua.deti.tqs.p11.support.TelemetryMessages.readingWithFault;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;
import pt.ua.deti.tqs.p11.support.FleetApi;

/**
 * Steps for US10 (KAN-62) and US11 (KAN-63), run against the real API, database and MQTT broker.
 * Ticket states are written as in the Product Specs (InProgress) and read as in the API
 * (IN_PROGRESS). A fault reading is waited for until the vehicle has applied it; the ticket is
 * opened in the same transaction, so after that its outcome is final, also when nothing was opened.
 */
public class MaintenanceSteps {

  private static final Duration WITHIN_SLO = Duration.ofSeconds(5);
  private static final String PASSWORD = "Passw0rd!";

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private RestClient http;
  private FleetApi operator;
  private ResponseEntity<Map<String, Object>> lastResponse;
  private final Map<String, FleetApi> technicians = new HashMap<>();
  private List<Map<String, Object>> technicianList;

  /** Runs before the hooks that delete vehicles and users, because tickets point to them. */
  @Before(order = 9000)
  public void startWithNoTickets() {
    jdbc.update("DELETE FROM maintenance_tickets");
    http = ApiClient.forPort(port);
  }

  @After
  public void removeTheTickets() {
    jdbc.update("DELETE FROM maintenance_tickets");
  }

  // ------------------------------------------------------------- helpers

  /** An operator of its own, as the other step classes have; created on first use. */
  private FleetApi operator() {
    if (operator == null) {
      Registered marta =
          ApiClient.registerGuest(http, "Marta Pires", "marta.pires@example.com", PASSWORD);
      jdbc.update("UPDATE users SET role = 'OPERATOR' WHERE id = ?", marta.id());
      operator = new FleetApi(http, marta.token());
    }
    return operator;
  }

  /** "InProgress" in the scenarios, "IN_PROGRESS" in the API. */
  private static String apiState(String state) {
    return state.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
  }

  private UUID vehicleId(String code) {
    return jdbc.queryForObject("SELECT id FROM vehicles WHERE code = ?", UUID.class, code);
  }

  private List<Map<String, Object>> openTicketsOf(String vehicleCode) {
    String vehicle = vehicleId(vehicleCode).toString();
    return operator().getList("/tickets").getBody().stream()
        .filter(ticket -> vehicle.equals(ticket.get("vehicleId")))
        .toList();
  }

  private Map<String, Object> openTicket(String vehicleCode, String fault) {
    return openTicketsOf(vehicleCode).stream()
        .filter(ticket -> fault.equals(ticket.get("faultCode")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No open ticket for " + fault));
  }

  private Map<String, Object> openTicketForFault(String fault) {
    return operator().getList("/tickets").getBody().stream()
        .filter(ticket -> fault.equals(ticket.get("faultCode")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No open ticket for " + fault));
  }

  private Instant lastReadingOf(String vehicleCode) {
    Timestamp at =
        jdbc.queryForObject(
            "SELECT last_reading_at FROM vehicles WHERE code = ?", Timestamp.class, vehicleCode);
    return at == null ? null : at.toInstant();
  }

  // ------------------------------------------------------------- fault readings

  @When("the simulator reports fault {string} for {string} at {string}")
  public void theSimulatorReportsAFault(String fault, String vehicleCode, String at) {
    CucumberSpringConfiguration.BROKER.publishTelemetry(
        FLEET, vehicleCode, readingWithFault(vehicleCode, "80", fault, at));
    Instant timestamp = Instant.parse(at);
    await()
        .atMost(WITHIN_SLO)
        .untilAsserted(() -> assertThat(lastReadingOf(vehicleCode)).isEqualTo(timestamp));
  }

  @Given("the simulator has reported fault {string} for {string} at {string}")
  public void theSimulatorHasReportedAFault(String fault, String vehicleCode, String at) {
    theSimulatorReportsAFault(fault, vehicleCode, at);
  }

  // ------------------------------------------------------------- open tickets

  @Then("vehicle {string} has these open tickets:")
  public void vehicleHasTheseOpenTickets(String vehicleCode, DataTable expected) {
    Tuple[] tickets =
        expected.asMaps().stream()
            .map(row -> tuple(row.get("fault"), apiState(row.get("state"))))
            .toArray(Tuple[]::new);

    assertThat(openTicketsOf(vehicleCode))
        .extracting(ticket -> ticket.get("faultCode"), ticket -> ticket.get("state"))
        .containsExactlyInAnyOrder(tickets);
  }

  /** BR19. */
  @Then("the SLA deadline of the ticket for fault {string} is {int} hours after it was opened")
  public void theDeadlineIsHoursAfterOpening(String fault, int hours) {
    Map<String, Object> ticket = openTicketForFault(fault);
    Instant openedAt = Instant.parse((String) ticket.get("openedAt"));
    Instant deadline = Instant.parse((String) ticket.get("slaDeadline"));

    assertThat(Duration.between(openedAt, deadline)).isEqualTo(Duration.ofHours(hours));
  }

  // ------------------------------------------------------------- manual tickets

  @When(
      "the operator opens a ticket for vehicle {string} with fault {string} and description {string}")
  public void theOperatorOpensATicket(String vehicleCode, String fault, String description) {
    Map<String, Object> body = new HashMap<>();
    body.put("vehicleId", vehicleId(vehicleCode));
    body.put("faultCode", fault);
    body.put("description", description);
    lastResponse = operator().send(HttpMethod.POST, "/tickets", body);
  }

  @Then("the ticket is opened")
  public void theTicketIsOpened() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  /** BR20: refused, and the operator is told which ticket covers the fault. */
  @Then("the ticket is refused because the fault is already covered by an open ticket")
  public void theTicketIsRefusedAsCovered() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(lastResponse)).containsKey("faultCode");
    assertThat(lastResponse.getBody()).containsKey("ticketId");
  }

  // ------------------------------------------------------------- assignment

  @Given("an account {string} with role {string} and field technician {word}")
  public void anAccountWithRole(String email, String role, String technician) {
    String name = email.substring(0, email.indexOf('@'));
    Registered account = ApiClient.registerGuest(http, name, email, PASSWORD);
    jdbc.update(
        "UPDATE users SET role = ?, field_technician = ? WHERE id = ?",
        role,
        Boolean.parseBoolean(technician),
        account.id());
  }

  @When("the operator assigns the ticket for fault {string} of vehicle {string} to {string}")
  public void theOperatorAssigns(String fault, String vehicleCode, String email) {
    String ticketId = (String) openTicket(vehicleCode, fault).get("id");
    UUID accountId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    lastResponse =
        operator()
            .send(
                HttpMethod.PATCH,
                "/tickets/" + ticketId + "/assignee",
                Map.of("technicianId", accountId));
  }

  @Then("the assignment is {word}")
  public void theAssignmentIs(String outcome) {
    HttpStatus expected =
        switch (outcome) {
          case "accepted" -> HttpStatus.OK;
          case "refused" -> HttpStatus.BAD_REQUEST;
          default ->
              throw new IllegalArgumentException("Outcome is accepted or refused: " + outcome);
        };
    assertThat(lastResponse.getStatusCode()).isEqualTo(expected);
  }

  // ------------------------------------------------------------- field technicians (US11)

  /** Any ticket of the vehicle for the fault, open or not, the latest first. */
  private UUID ticketId(String fault, String vehicleCode) {
    return jdbc.queryForObject(
        "SELECT t.id FROM maintenance_tickets t JOIN vehicles v ON v.id = t.vehicle_id"
            + " WHERE v.code = ? AND t.fault_code = ? ORDER BY t.opened_at DESC LIMIT 1",
        UUID.class,
        vehicleCode,
        fault);
  }

  private UUID accountId(String email) {
    return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
  }

  private FleetApi technician(String email) {
    FleetApi technician = technicians.get(email);
    if (technician == null) {
      throw new IllegalStateException("Field technician " + email + " is not logged in");
    }
    return technician;
  }

  private ResponseEntity<Map<String, Object>> start(String email, String fault, String vehicle) {
    return technician(email)
        .send(HttpMethod.POST, "/tickets/" + ticketId(fault, vehicle) + "/start", Map.of());
  }

  private void assignTo(String fault, String vehicleCode, String email) {
    lastResponse =
        operator()
            .send(
                HttpMethod.PATCH,
                "/tickets/" + openTicket(vehicleCode, fault).get("id") + "/assignee",
                Map.of("technicianId", accountId(email)));
  }

  @Given("field technician {string} is logged in")
  public void fieldTechnicianIsLoggedIn(String email) {
    String name = email.substring(0, email.indexOf('@'));
    Registered account = ApiClient.registerGuest(http, name, email, PASSWORD);
    jdbc.update(
        "UPDATE users SET role = 'OPERATOR', field_technician = TRUE WHERE id = ?", account.id());
    technicians.put(email, new FleetApi(http, account.token()));
  }

  @Given("the ticket for fault {string} of vehicle {string} is assigned to {string}")
  public void theTicketIsAssignedTo(String fault, String vehicleCode, String email) {
    assignTo(fault, vehicleCode, email);
    assertThat(lastResponse.getStatusCode()).as("setup: assignment").isEqualTo(HttpStatus.OK);
  }

  /** Moves the opening and the deadline back, so a scenario does not wait for the clock. */
  @Given("the ticket for fault {string} of vehicle {string} was opened {int} hours ago")
  public void theTicketWasOpenedHoursAgo(String fault, String vehicleCode, int hours) {
    jdbc.update(
        "UPDATE maintenance_tickets SET opened_at = opened_at - make_interval(hours => ?),"
            + " sla_deadline = sla_deadline - make_interval(hours => ?) WHERE id = ?",
        hours,
        hours,
        ticketId(fault, vehicleCode));
  }

  @Given("{string} has started the ticket for fault {string} of vehicle {string}")
  public void hasStartedTheTicket(String email, String fault, String vehicleCode) {
    assertThat(start(email, fault, vehicleCode).getStatusCode())
        .as("setup: start")
        .isEqualTo(HttpStatus.OK);
  }

  @When("{string} looks at their tickets")
  public void looksAtTheirTickets(String email) {
    technicianList = technician(email).getList("/tickets/mine").getBody();
  }

  @When("{string} starts the ticket for fault {string} of vehicle {string}")
  public void startsTheTicket(String email, String fault, String vehicleCode) {
    lastResponse = start(email, fault, vehicleCode);
  }

  @When("{string} closes the ticket for fault {string} of vehicle {string} recording {string}")
  public void closesTheTicket(String email, String fault, String vehicleCode, String workDone) {
    Map<String, Object> body = new HashMap<>();
    body.put("workDone", workDone);
    lastResponse =
        technician(email)
            .send(HttpMethod.POST, "/tickets/" + ticketId(fault, vehicleCode) + "/close", body);
  }

  /** The location of a ticket is the code of the hub its vehicle is at. */
  @Then("they see these tickets, in order:")
  public void theySeeTheseTicketsInOrder(DataTable expected) {
    Tuple[] tickets =
        expected.asMaps().stream()
            .map(row ->
                tuple(
                    row.get("vehicle"),
                    row.get("fault"),
                    row.get("location"),
                    "yes".equals(row.get("breached"))))
            .toArray(Tuple[]::new);

    assertThat(technicianList)
        .extracting(
            ticket -> ticket.get("vehicleCode"),
            ticket -> ticket.get("faultCode"),
            ticket -> ticket.get("hubCode"),
            ticket -> ticket.get("breached"))
        .containsExactly(tickets);
  }

  // Exact sentences, not "{}": US04 has "the start is refused because {}" for a ride.

  @Then("starting the ticket is refused because it is not theirs")
  public void startingIsRefusedAsNotTheirs() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Then("the closing is accepted")
  public void theClosingIsAccepted() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Then("the closing is refused because the work done is empty")
  public void theClosingIsRefusedAsEmpty() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(lastResponse)).containsKey("workDone");
  }

  @Then("the closing is refused because the work has not started")
  public void theClosingIsRefusedAsNotStarted() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(lastResponse)).containsKey("state");
  }

  @Then("the closing is refused because the ticket is not theirs")
  public void theClosingIsRefusedAsNotTheirs() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Then("the ticket for fault {string} of vehicle {string} is {string}")
  public void theTicketIs(String fault, String vehicleCode, String state) {
    assertThat(
            jdbc.queryForObject(
                "SELECT state FROM maintenance_tickets WHERE id = ?",
                String.class,
                ticketId(fault, vehicleCode)))
        .isEqualTo(apiState(state));
  }

  /** BR19, as the API reported it when the ticket was closed. */
  @Then("the ticket for fault {string} of vehicle {string} was closed on time")
  public void theTicketWasClosedOnTime(String fault, String vehicleCode) {
    assertClosedBreached(fault, vehicleCode, false);
  }

  @Then("the ticket for fault {string} of vehicle {string} was closed after its deadline")
  public void theTicketWasClosedLate(String fault, String vehicleCode) {
    assertClosedBreached(fault, vehicleCode, true);
  }

  private void assertClosedBreached(String fault, String vehicleCode, boolean breached) {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(lastResponse.getBody())
        .containsEntry("id", ticketId(fault, vehicleCode).toString())
        .containsEntry("breached", breached);
  }

  /** A few minutes of slack: the scenario itself takes some time after moving the opening back. */
  @Then("its time to repair is about {int} hours")
  public void itsTimeToRepairIsAbout(int hours) {
    long minutes = ((Number) lastResponse.getBody().get("timeToRepairMinutes")).longValue();

    assertThat(minutes).isBetween(hours * 60L, hours * 60L + 5);
  }
}
