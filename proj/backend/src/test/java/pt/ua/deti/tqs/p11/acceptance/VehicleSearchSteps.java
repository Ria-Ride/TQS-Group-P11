package pt.ua.deti.tqs.p11.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;
import pt.ua.deti.tqs.p11.support.FleetApi;

/**
 * Steps for US02 (KAN-54), run against the real API and database. The fleet is set up by an
 * operator through the API; the tourist only searches. Battery levels, Reserved and Maintenance are
 * written to the database because telemetry (KAN-92), bookings (US04) and tickets (US10) do not
 * exist yet. The database is cleaned before every scenario by the hooks in FleetSteps and
 * GuestAccountSteps.
 */
public class VehicleSearchSteps {

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private FleetApi operator;
  private FleetApi tourist;
  private final Map<String, UUID> hubs = new HashMap<>();
  private List<Map<String, Object>> result = List.of();
  private ResponseEntity<Map<String, Object>> refusal;
  private HttpStatusCode lastStatus;

  // ------------------------------------------------------------- helpers

  private UUID hub(String code) {
    return hubs.get(code);
  }

  private void setBattery(String vehicleCode, Integer percent) {
    jdbc.update("UPDATE vehicles SET battery_percent = ? WHERE code = ?", percent, vehicleCode);
  }

  private void availableVehicle(String code, String type, String hubCode, Integer battery) {
    UUID id = FleetApi.id(FleetApi.created(operator.registerVehicle(code, type, hub(hubCode))));
    assertThat(operator.transition(id, "ACTIVATE").getStatusCode())
        .as("setup: activate %s", code)
        .isEqualTo(HttpStatus.OK);
    setBattery(code, battery);
  }

  private void search(String hubCode, String query) {
    ResponseEntity<List<Map<String, Object>>> response =
        tourist.getList("/hubs/" + hub(hubCode) + "/vehicles" + query);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    result = response.getBody();
  }

  private Map<String, Object> shown(String code) {
    return result.stream()
        .filter(vehicle -> code.equals(vehicle.get("code")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Vehicle %s is not in %s".formatted(code, result)));
  }

  // ------------------------------------------------------------- setup

  @Given("a tourist is logged in")
  public void aTouristIsLoggedIn() {
    RestClient http = ApiClient.forPort(port);
    Registered rui =
        ApiClient.registerGuest(http, "Rui Costa", "rui.costa@example.com", "Passw0rd!");
    // Roles are set in the database and read on every request, so the token works right away.
    jdbc.update("UPDATE users SET role = 'OPERATOR' WHERE id = ?", rui.id());
    operator = new FleetApi(http, rui.token());
    Registered ana =
        ApiClient.registerGuest(http, "Ana Lopes", "ana.lopes@example.com", "Passw0rd!");
    tourist = new FleetApi(http, ana.token());
  }

  @Given("hub {string} exists")
  public void hubExists(String code) {
    hubs.put(code, operator.hub(code, 10));
  }

  @Given("hub {string} holds these available vehicles:")
  public void hubHoldsTheseAvailableVehicles(String hubCode, DataTable table) {
    for (Map<String, String> row : table.asMaps()) {
      availableVehicle(
          row.get("code"), row.get("type"), hubCode, Integer.valueOf(row.get("battery")));
    }
  }

  @Given("hub {string} holds an available vehicle {string} that has not reported its battery")
  public void hubHoldsAVehicleWithoutAReading(String hubCode, String code) {
    availableVehicle(code, "E_BIKE", hubCode, null);
  }

  @Given("hub {string} holds vehicle {string} in state {string} with a battery of {int}%")
  public void hubHoldsAVehicleInState(String hubCode, String code, String state, int battery) {
    if ("Deactivated".equals(state)) {
      operator.bike(code, hub(hubCode));
    } else {
      availableVehicle(code, "E_BIKE", hubCode, battery);
      jdbc.update("UPDATE vehicles SET state = ? WHERE code = ?", state.toUpperCase(), code);
    }
    setBattery(code, battery);
  }

  @Given("the tourist searched hub {string}")
  public void theTouristSearchedHub(String hubCode) {
    search(hubCode, "");
  }

  // ------------------------------------------------------------- search

  @When("vehicle {string} reports a battery of {int}%")
  public void vehicleReportsABattery(String code, int percent) {
    setBattery(code, percent);
  }

  @When("the tourist searches hub {string}")
  public void theTouristSearchesHub(String hubCode) {
    search(hubCode, "");
  }

  @When("the tourist searches hub {string} for type {string}")
  public void theTouristSearchesHubForType(String hubCode, String type) {
    search(hubCode, "?type=" + type);
  }

  @When("the tourist searches hub {string} to ride for {int} minutes")
  public void theTouristSearchesHubToRideFor(String hubCode, int minutes) {
    String uri = "/hubs/" + hub(hubCode) + "/vehicles?minutes=" + minutes;
    lastStatus = tourist.status(uri);
    result = lastStatus.is2xxSuccessful() ? tourist.getList(uri).getBody() : List.of();
  }

  @When("the tourist searches a hub that does not exist")
  public void theTouristSearchesAnUnknownHub() {
    refusal = tourist.get("/hubs/" + UUID.randomUUID() + "/vehicles");
  }

  // ------------------------------------------------------------- outcome

  @Then("the tourist sees these vehicles:")
  public void theTouristSeesTheseVehicles(DataTable table) {
    List<Map<String, String>> expected = table.asMaps();
    assertThat(result).hasSize(expected.size());
    for (Map<String, String> row : expected) {
      assertThat(shown(row.get("code")))
          .containsEntry("type", row.get("type"))
          .containsEntry("batteryPercent", Integer.valueOf(row.get("battery")))
          .containsEntry("bookable", "yes".equals(row.get("bookable")));
    }
  }

  @Then("the tourist sees only vehicles of type {string}")
  public void theTouristSeesOnlyType(String type) {
    assertThat(result)
        .isNotEmpty()
        .allSatisfy(vehicle -> assertThat(vehicle).containsEntry("type", type));
  }

  @Then("vehicle {string} is shown as bookable")
  public void vehicleIsShownAsBookable(String code) {
    assertThat(shown(code)).containsEntry("bookable", true);
  }

  @Then("vehicle {string} is shown as not bookable")
  public void vehicleIsShownAsNotBookable(String code) {
    assertThat(shown(code)).containsEntry("bookable", false);
  }

  @Then("the tourist sees no vehicles")
  public void theTouristSeesNoVehicles() {
    assertThat(result).isEmpty();
  }

  @Then("no vehicle is bookable")
  public void noVehicleIsBookable() {
    assertThat(result)
        .isNotEmpty()
        .allSatisfy(vehicle -> assertThat(vehicle).containsEntry("bookable", false));
  }

  @Then("vehicle {string} needs a battery of {int}%")
  public void vehicleNeedsABattery(String code, int percent) {
    assertThat(shown(code)).containsEntry("requiredBatteryPercent", percent);
  }

  @Then("the search is refused as invalid")
  public void theSearchIsRefusedAsInvalid() {
    assertThat(lastStatus).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Then("the search is refused as not found")
  public void theSearchIsRefusedAsNotFound() {
    assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
