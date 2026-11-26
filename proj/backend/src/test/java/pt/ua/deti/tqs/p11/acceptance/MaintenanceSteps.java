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
 * Steps for US10 (KAN-62), run against the real API, database and MQTT broker. Ticket states are
 * written as in the Product Specs (InProgress) and read as in the API (IN_PROGRESS). A fault
 * reading is waited for until the vehicle has applied it; the ticket is opened in the same
 * transaction, so after that its outcome is final, also when nothing was opened.
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
}
