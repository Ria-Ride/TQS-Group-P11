package pt.ua.deti.tqs.p11.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.ua.deti.tqs.p11.support.FleetApi.created;
import static pt.ua.deti.tqs.p11.support.FleetApi.errors;
import static pt.ua.deti.tqs.p11.support.FleetApi.id;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;
import pt.ua.deti.tqs.p11.support.FleetApi;

/**
 * Steps for US08 (KAN-60), run against the real API and database. States are written as in the
 * Product Specs (InTransit) and sent as in the API (IN_TRANSIT). The database is only written
 * directly for states no API reaches yet: Reserved and InUse (bookings, US04/US05) and Maintenance
 * (tickets, US10).
 */
public class FleetSteps {

  private static final String DEFAULT_HUB = "hub-03";
  private static final int DEFAULT_CAPACITY = 10;

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private FleetApi operator;
  private ResponseEntity<Map<String, Object>> lastResponse;

  @Before
  public void startWithAnEmptyFleet() {
    jdbc.update("DELETE FROM vehicle_state_changes");
    jdbc.update("DELETE FROM vehicles");
    jdbc.update("DELETE FROM hubs");
  }

  // ------------------------------------------------------------- helpers

  /** "InTransit" in the scenarios, "IN_TRANSIT" in the API. */
  private static String apiState(String state) {
    return state.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
  }

  /**
   * "an operator is logged in" creates the operator; features that only check vehicles (US04) get
   * one on first use, with its own email so it never clashes with the accounts of other steps.
   */
  private FleetApi operator() {
    if (operator == null) {
      RestClient client = ApiClient.forPort(port);
      Registered checker =
          ApiClient.registerGuest(
              client, "Fleet Checker", "fleet.checker@example.com", "Passw0rd!");
      jdbc.update("UPDATE users SET role = 'OPERATOR' WHERE id = ?", checker.id());
      operator = new FleetApi(client, checker.token());
    }
    return operator;
  }

  private UUID hubId(String code) {
    return operator.getList("/hubs").getBody().stream()
        .filter(hub -> code.equals(hub.get("code")))
        .map(hub -> UUID.fromString((String) hub.get("id")))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No hub " + code));
  }

  private UUID vehicleId(String code) {
    return operator().getList("/vehicles").getBody().stream()
        .filter(vehicle -> code.equals(vehicle.get("code")))
        .map(vehicle -> UUID.fromString((String) vehicle.get("id")))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No vehicle " + code));
  }

  private boolean hubExists(String code) {
    return operator.getList("/hubs").getBody().stream().anyMatch(h -> code.equals(h.get("code")));
  }

  private Map<String, Object> hub(String code) {
    return operator.get("/hubs/" + hubId(code)).getBody();
  }

  private Map<String, Object> vehicle(String code) {
    return operator().get("/vehicles/" + vehicleId(code)).getBody();
  }

  private UUID createHub(String code, int capacity) {
    return id(created(operator.registerHub(code, "Hub " + code, 40.6405, -8.6538, capacity)));
  }

  /** Setup transitions must work; a failure here is a broken setup, not the scenario's outcome. */
  private void setupTransition(UUID vehicle, String action) {
    assertThat(operator.transition(vehicle, action).getStatusCode())
        .as("setup: %s", action)
        .isEqualTo(HttpStatus.OK);
  }

  /** Registers the vehicle at the hub and brings it to the state, through the API where it can. */
  private void vehicleInState(String code, String state, UUID hub) {
    UUID vehicle = operator.bike(code, hub);
    switch (state) {
      case "Deactivated" -> {
        // Registered vehicles start Deactivated.
      }
      case "Available" -> setupTransition(vehicle, "ACTIVATE");
      case "InTransit" -> {
        setupTransition(vehicle, "ACTIVATE");
        setupTransition(vehicle, "START_MOVE");
      }
      case "Retired" -> setupTransition(vehicle, "RETIRE");
      case "Reserved", "Maintenance" -> {
        setupTransition(vehicle, "ACTIVATE");
        jdbc.update("UPDATE vehicles SET state = ? WHERE id = ?", apiState(state), vehicle);
      }
      case "InUse" -> {
        setupTransition(vehicle, "ACTIVATE");
        jdbc.update("UPDATE vehicles SET state = 'IN_USE', hub_id = NULL WHERE id = ?", vehicle);
      }
      default -> throw new IllegalArgumentException("Unknown vehicle state: " + state);
    }
  }

  private void assertStatus(String outcome, HttpStatus accepted, HttpStatus refused) {
    HttpStatus expected =
        switch (outcome) {
          case "accepted" -> accepted;
          case "refused" -> refused;
          default ->
              throw new IllegalArgumentException("Outcome is accepted or refused: " + outcome);
        };
    assertThat(lastResponse.getStatusCode()).isEqualTo(expected);
  }

  private void assertConflictOn(String field) {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(lastResponse)).containsKey(field);
  }

  // ------------------------------------------------------------- operator

  @Given("an operator is logged in")
  public void anOperatorIsLoggedIn() {
    RestClient http = ApiClient.forPort(port);
    Registered rui =
        ApiClient.registerGuest(http, "Rui Costa", "rui.costa@example.com", "Passw0rd!");
    // Roles are set in the database and read on every request, so the token works right away.
    jdbc.update("UPDATE users SET role = 'OPERATOR' WHERE id = ?", rui.id());
    operator = new FleetApi(http, rui.token());
  }

  // ------------------------------------------------------------- hubs

  @Given("hub {string} with capacity {int} exists")
  public void hubWithCapacityExists(String code, int capacity) {
    createHub(code, capacity);
  }

  @Given("hub {string} with capacity {int} holds {int} vehicles")
  public void hubWithCapacityHolds(String code, int capacity, int held) {
    UUID hub = createHub(code, capacity);
    for (int i = 1; i <= held; i++) {
      operator.bike("%s-filler-%02d".formatted(code, i), hub);
    }
  }

  @Given("hub {string} has capacity {int} and its only vehicle {string} is {string}")
  public void hubHasOnlyVehicle(String hubCode, int capacity, String vehicleCode, String state) {
    vehicleInState(vehicleCode, state, createHub(hubCode, capacity));
  }

  @When(
      "the operator registers hub {string} named {string} at {double}, {double} with capacity {int}")
  public void theOperatorRegistersHub(
      String code, String name, double latitude, double longitude, int capacity) {
    lastResponse = operator.registerHub(code, name, latitude, longitude, capacity);
  }

  @When("the operator changes the capacity of hub {string} to {int}")
  public void theOperatorChangesTheCapacity(String code, int capacity) {
    lastResponse = operator.changeCapacity(hubId(code), capacity);
  }

  @Then("hub {string} exists with capacity {int} and holds {int} vehicles")
  public void hubExistsWithCapacityAndHolds(String code, int capacity, int held) {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(hub(code)).containsEntry("capacity", capacity).containsEntry("vehiclesHeld", held);
  }

  @Then("the hub is refused as invalid")
  public void theHubIsRefusedAsInvalid() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Then("the hub is refused because the code is already in use")
  public void theHubIsRefusedBecauseTheCodeIsInUse() {
    assertConflictOn("code");
  }

  @Then("hub {string} does not exist")
  public void hubDoesNotExist(String code) {
    assertThat(hubExists(code)).isFalse();
  }

  @Then("the capacity change is {word}")
  public void theCapacityChangeIs(String outcome) {
    assertStatus(outcome, HttpStatus.OK, HttpStatus.CONFLICT);
  }

  @Then("hub {string} has capacity {int}")
  public void hubHasCapacity(String code, int capacity) {
    assertThat(hub(code)).containsEntry("capacity", capacity);
  }

  @Then("hub {string} holds {int} vehicles")
  public void hubHolds(String code, int held) {
    assertThat(hub(code)).containsEntry("vehiclesHeld", held);
  }

  // ------------------------------------------------------------- vehicles

  @Given("a vehicle {string} in state {string}")
  public void aVehicleInState(String code, String state) {
    if (!hubExists(DEFAULT_HUB)) {
      createHub(DEFAULT_HUB, DEFAULT_CAPACITY);
    }
    vehicleInState(code, state, hubId(DEFAULT_HUB));
  }

  @When("the operator registers vehicle {string} of type {string} at hub {string}")
  public void theOperatorRegistersVehicle(String code, String type, String hub) {
    lastResponse = operator.registerVehicle(code, type, hubId(hub));
  }

  @Then("the vehicle registration is {word}")
  public void theVehicleRegistrationIs(String outcome) {
    assertStatus(outcome, HttpStatus.CREATED, HttpStatus.CONFLICT);
  }

  @Then("the vehicle registration is refused because the hub is full")
  public void theVehicleRegistrationIsRefusedBecauseTheHubIsFull() {
    assertConflictOn("hubId");
  }

  @Then("the vehicle registration is refused because the code is already in use")
  public void theVehicleRegistrationIsRefusedBecauseTheCodeIsInUse() {
    assertConflictOn("code");
  }

  @Then("vehicle {string} does not exist")
  public void vehicleDoesNotExist(String code) {
    assertThat(operator.getList("/vehicles").getBody())
        .noneMatch(vehicle -> code.equals(vehicle.get("code")));
  }

  // ------------------------------------------------------------- transitions (BR16)

  @When("the operator activates vehicle {string}")
  public void theOperatorActivates(String code) {
    lastResponse = operator.transition(vehicleId(code), "ACTIVATE");
  }

  @When("the operator deactivates vehicle {string}")
  public void theOperatorDeactivates(String code) {
    lastResponse = operator.transition(vehicleId(code), "DEACTIVATE");
  }

  @When("the operator starts moving vehicle {string}")
  public void theOperatorStartsMoving(String code) {
    lastResponse = operator.transition(vehicleId(code), "START_MOVE");
  }

  @When("the operator confirms the arrival of vehicle {string} at hub {string}")
  public void theOperatorConfirmsTheArrival(String code, String hub) {
    lastResponse = operator.transition(vehicleId(code), "CONFIRM_ARRIVAL", hubId(hub));
  }

  @When("the operator retires vehicle {string}")
  public void theOperatorRetires(String code) {
    lastResponse = operator.transition(vehicleId(code), "RETIRE");
  }

  /** The actions of the scenario outlines, in words. */
  @When("the operator asks to {} vehicle {string}")
  public void theOperatorAsksTo(String action, String code) {
    String apiAction =
        switch (action) {
          case "activate" -> "ACTIVATE";
          case "deactivate" -> "DEACTIVATE";
          case "start moving" -> "START_MOVE";
          case "retire" -> "RETIRE";
          default -> throw new IllegalArgumentException("Unknown action: " + action);
        };
    lastResponse = operator.transition(vehicleId(code), apiAction);
  }

  @Then("the transition is {word}")
  public void theTransitionIs(String outcome) {
    assertStatus(outcome, HttpStatus.OK, HttpStatus.CONFLICT);
  }

  @Then("the transition is refused because the hub is full")
  public void theTransitionIsRefusedBecauseTheHubIsFull() {
    assertConflictOn("hubId");
  }

  @Then("vehicle {string} is {string}")
  public void vehicleIs(String code, String state) {
    assertThat(vehicle(code)).containsEntry("state", apiState(state));
  }

  @Then("vehicle {string} is {string} at hub {string}")
  public void vehicleIsAtHub(String code, String state, String hub) {
    assertThat(vehicle(code))
        .containsEntry("state", apiState(state))
        .containsEntry("hubId", hubId(hub).toString());
  }

  @Then("vehicle {string} is {string} and belongs to no hub")
  public void vehicleIsAtNoHub(String code, String state) {
    assertThat(vehicle(code)).containsEntry("state", apiState(state)).containsEntry("hubId", null);
  }

  /** An empty "from" cell is the registration, which has no previous state. */
  @Then("the state history of vehicle {string} is, in order:")
  public void theStateHistoryIs(String code, DataTable expected) {
    List<String> expectedChanges =
        expected.asMaps().stream()
            .map(row -> change(row.get("from"), row.get("to"), true))
            .toList();

    List<String> actualChanges =
        operator.getList("/vehicles/" + vehicleId(code) + "/history").getBody().stream()
            .map(row -> change((String) row.get("from"), (String) row.get("to"), false))
            .toList();

    assertThat(actualChanges).containsExactlyElementsOf(expectedChanges);
  }

  private static String change(String from, String to, boolean inScenarioWords) {
    String fromState = Objects.requireNonNullElse(from, "");
    if (inScenarioWords) {
      return (fromState.isEmpty() ? "" : apiState(fromState)) + " > " + apiState(to);
    }
    return fromState + " > " + to;
  }
}
