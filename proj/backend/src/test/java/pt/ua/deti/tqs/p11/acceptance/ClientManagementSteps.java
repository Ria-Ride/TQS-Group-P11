package pt.ua.deti.tqs.p11.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Steps for US14 (KAN-66), run against the real API and database as the client manager and, when a
 * scenario needs them, an operator and a tourist. Hotels and pass types a scenario makes start with
 * CUKE and the rules versions above 1 are removed afterwards, so each scenario starts from the
 * seeded data.
 */
public class ClientManagementSteps {

  private static final ZoneId HOTEL_ZONE = ZoneId.of("Europe/Lisbon");

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private FleetApi manager;
  private FleetApi operator;
  private FleetApi tourist;
  private ResponseEntity<Map<String, Object>> last;
  private String lastStayCode;

  @After
  public void removeWhatTheScenarioMade() {
    jdbc.update("DELETE FROM rules_of_use_vehicle_types WHERE version > 1");
    jdbc.update("DELETE FROM rules_of_use WHERE version > 1");
    jdbc.update(
        "DELETE FROM pass_type_vehicle_types WHERE pass_type_id IN"
            + " (SELECT id FROM pass_types WHERE code LIKE 'CUKE\\_%')");
    jdbc.update("DELETE FROM pass_types WHERE code LIKE 'CUKE\\_%'");
    jdbc.update(
        "DELETE FROM stays WHERE accommodation_id IN"
            + " (SELECT id FROM partner_accommodations WHERE name LIKE 'CUKE %')");
    jdbc.update("DELETE FROM partner_accommodations WHERE name LIKE 'CUKE %'");
  }

  // ------------------------------------------------------------- accounts

  private FleetApi account(String name, String email, String role) {
    RestClient http = ApiClient.forPort(port);
    Registered registered = ApiClient.registerGuest(http, name, email, "Passw0rd!");
    if (role != null) {
      jdbc.update("UPDATE users SET role = ? WHERE id = ?", role, registered.id());
    }
    return new FleetApi(http, registered.token());
  }

  @Given("a client manager is logged in")
  public void aClientManagerIsLoggedIn() {
    manager = account("Mara Silva", "mara.silva@example.com", "CLIENT_MANAGER");
  }

  private FleetApi operator() {
    if (operator == null) {
      operator = account("Rui Costa", "rui.costa.cm@example.com", "OPERATOR");
    }
    return operator;
  }

  private FleetApi tourist() {
    if (tourist == null) {
      tourist = account("Ana Lopes", "ana.lopes.cm@example.com", null);
    }
    return tourist;
  }

  // ------------------------------------------------------------- rules of use

  private static Map<String, Object> validRules() {
    Map<String, Object> body = new HashMap<>();
    body.put("maxBookingMinutes", 240);
    body.put("minBatteryPercent", 25);
    body.put("startWindowMinutes", 20);
    body.put("repositioningWindowMinutes", 12);
    body.put("overtimeGraceMinutes", 0);
    body.put("overtimeBlockMinutes", 30);
    body.put("overtimeRatePerBlock", "2.00");
    body.put("batteryPercentPer30Minutes", Map.of("E_BIKE", 4, "E_SCOOTER", 9));
    return body;
  }

  private void putRules(Map<String, Object> body) {
    last = manager.send(HttpMethod.PUT, "/usage-rules", body);
  }

  @When("the client manager sets the rules of use:")
  public void theClientManagerSetsTheRulesOfUse(DataTable table) {
    Map<String, String> row = table.asMaps().getFirst();
    Map<String, Object> body = new HashMap<>();
    body.put("maxBookingMinutes", Integer.valueOf(row.get("maxBookingMinutes")));
    body.put("minBatteryPercent", Integer.valueOf(row.get("minBatteryPercent")));
    body.put("startWindowMinutes", Integer.valueOf(row.get("startWindowMinutes")));
    body.put("repositioningWindowMinutes", Integer.valueOf(row.get("repositioningWindowMinutes")));
    body.put("overtimeGraceMinutes", Integer.valueOf(row.get("overtimeGraceMinutes")));
    body.put("overtimeBlockMinutes", Integer.valueOf(row.get("overtimeBlockMinutes")));
    body.put("overtimeRatePerBlock", row.get("overtimeRatePerBlock"));
    body.put(
        "batteryPercentPer30Minutes",
        Map.of(
            "E_BIKE", Integer.valueOf(row.get("bikePercentPer30Minutes")),
            "E_SCOOTER", Integer.valueOf(row.get("scooterPercentPer30Minutes"))));
    putRules(body);
  }

  @When("the client manager sets the rules of use with {word} at {int}")
  public void theClientManagerSetsTheRulesOfUseWith(String field, int value) {
    Map<String, Object> body = validRules();
    body.put(field, value);
    putRules(body);
  }

  @Given("the client manager has set the rules of use with {word} at {int}")
  public void theClientManagerHasSetTheRulesOfUseWith(String field, int value) {
    theClientManagerSetsTheRulesOfUseWith(field, value);
    assertThat(last.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Then("the rules of use are at version {int}")
  public void theRulesOfUseAreAtVersion(int version) {
    assertThat(manager.get("/usage-rules").getBody()).containsEntry("version", version);
  }

  @Then("the longest booking is {int} minutes and the minimum battery is {int}%")
  public void theLongestBookingAndTheMinimumBattery(int minutes, int percent) {
    assertThat(manager.get("/usage-rules").getBody())
        .containsEntry("maxBookingMinutes", minutes)
        .containsEntry("minBatteryPercent", percent);
  }

  @Then("the rules of use are refused because of {word}")
  public void theRulesOfUseAreRefusedBecauseOf(String field) {
    assertThat(last.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(FleetApi.errors(last)).containsKey(field);
  }

  @Then("version {int} of the rules of use has a longest booking of {int} minutes")
  public void versionHasALongestBooking(int version, int minutes) {
    List<Map<String, Object>> versions = manager.getList("/usage-rules/versions").getBody();
    assertThat(versions)
        .filteredOn(found -> Integer.valueOf(version).equals(found.get("version")))
        .singleElement()
        .satisfies(found -> assertThat(found).containsEntry("maxBookingMinutes", minutes));
  }

  // ------------------------------------------------------------- deposits

  @When("the client manager sets the deposit of {string} to {string}")
  public void theClientManagerSetsTheDeposit(String type, String deposit) {
    last =
        manager.send(HttpMethod.PATCH, "/vehicle-types/" + type, Map.of("depositAmount", deposit));
  }

  @Then("the deposit of {string} is {string}")
  public void theDepositIs(String type, String deposit) {
    assertThat(manager.getList("/vehicle-types").getBody())
        .filteredOn(found -> type.equals(found.get("type")))
        .singleElement()
        .satisfies(found -> assertThat(found).containsEntry("depositAmount", deposit));
  }

  @Then("the deposit is refused as invalid")
  public void theDepositIsRefusedAsInvalid() {
    assertThat(last.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(FleetApi.errors(last)).containsKey("depositAmount");
  }

  // ------------------------------------------------------------- hub capacity

  @Given("an operator has registered hub {string} with capacity {int} and {int} vehicles")
  public void anOperatorHasRegisteredAHub(String code, int capacity, int vehicles) {
    UUID hub = operator().hub(code, capacity);
    for (int i = 1; i <= vehicles; i++) {
      operator().bike("%s-bike-%d".formatted(code, i), hub);
    }
  }

  private UUID hubId(String code) {
    return manager.getList("/hubs").getBody().stream()
        .filter(found -> code.equals(found.get("code")))
        .map(found -> UUID.fromString((String) found.get("id")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No hub " + code));
  }

  @When("the client manager changes the capacity of hub {string} to {int}")
  public void theClientManagerChangesTheCapacity(String code, int capacity) {
    last = manager.changeCapacity(hubId(code), capacity);
  }

  @Then("hub {string} has capacity {int} for the client manager")
  public void hubHasCapacity(String code, int capacity) {
    assertThat(manager.get("/hubs/" + hubId(code)).getBody()).containsEntry("capacity", capacity);
  }

  @Then("the change of capacity is {word}")
  public void theChangeOfCapacityIs(String outcome) {
    HttpStatus expected =
        switch (outcome) {
          case "accepted" -> HttpStatus.OK;
          case "refused" -> HttpStatus.CONFLICT;
          default -> throw new IllegalArgumentException("accepted or refused: " + outcome);
        };
    assertThat(last.getStatusCode()).isEqualTo(expected);
  }

  // ------------------------------------------------------------- pass types

  @When(
      "the client manager creates pass type {string} named {string} valid {int} days at {string} for {string}")
  public void theClientManagerCreatesAPassType(
      String code, String name, int days, String price, String vehicleType) {
    Map<String, Object> body = new HashMap<>();
    body.put("code", code);
    body.put("name", name);
    body.put("validityDays", days);
    body.put("price", price);
    body.put("vehicleTypes", List.of(vehicleType));
    last = manager.send(HttpMethod.POST, "/pass-types", body);
  }

  @Given(
      "the client manager has created pass type {string} named {string} valid {int} days at {string} for {string}")
  public void theClientManagerHasCreatedAPassType(
      String code, String name, int days, String price, String vehicleType) {
    theClientManagerCreatesAPassType(code, name, days, price, vehicleType);
    assertThat(last.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  private Map<String, Object> passType(String code) {
    return tourist().getList("/pass-types").getBody().stream()
        .filter(found -> code.equals(found.get("code")))
        .findFirst()
        .orElse(null);
  }

  @When("the client manager changes the price of pass type {string} to {string}")
  public void theClientManagerChangesThePrice(String code, String price) {
    last =
        manager.send(
            HttpMethod.PATCH, "/pass-types/" + passType(code).get("id"), Map.of("price", price));
    assertThat(last.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Then("pass type {string} is valid {int} days and costs {string}")
  public void thePassTypeIsValidAndCosts(String code, int days, String price) {
    assertThat(passType(code)).containsEntry("validityDays", days).containsEntry("price", price);
  }

  @Then("the pass type is refused because the code is already in use")
  public void thePassTypeIsRefusedBecauseTheCodeIsInUse() {
    assertThat(last.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(FleetApi.errors(last)).containsKey("code");
  }

  @Then("the pass type is refused as invalid")
  public void thePassTypeIsRefusedAsInvalid() {
    assertThat(last.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Then("there is no pass type {string}")
  public void thereIsNoPassType(String code) {
    assertThat(passType(code)).isNull();
  }

  // ------------------------------------------------------------- hotels and stay codes

  private static String date(String text) {
    LocalDate today = LocalDate.now(HOTEL_ZONE);
    return switch (text) {
      case "yesterday" -> today.minusDays(1).toString();
      case "today" -> today.toString();
      case "tomorrow" -> today.plusDays(1).toString();
      default -> text;
    };
  }

  @When("the client manager registers hotel {string} with a discount of {int}%")
  public void theClientManagerRegistersAHotel(String name, int discount) {
    last =
        manager.send(
            HttpMethod.POST,
            "/partner-accommodations",
            Map.of("name", name, "discountPercent", discount));
  }

  @Given("the client manager has registered hotel {string} with a discount of {int}%")
  public void theClientManagerHasRegisteredAHotel(String name, int discount) {
    theClientManagerRegistersAHotel(name, discount);
    assertThat(last.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @When("the client manager issues a stay code for hotel {string} from {string} to {string}")
  public void theClientManagerIssuesAStayCode(String hotel, String from, String to) {
    UUID hotelId =
        manager.getList("/partner-accommodations").getBody().stream()
            .filter(found -> hotel.equals(found.get("name")))
            .map(found -> UUID.fromString((String) found.get("id")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No hotel " + hotel));
    Map<String, Object> body = new HashMap<>();
    body.put("accommodationId", hotelId);
    body.put("checkIn", date(from));
    body.put("checkOut", date(to));
    last = manager.send(HttpMethod.POST, "/stay-codes", body);
    if (last.getStatusCode() == HttpStatus.CREATED) {
      lastStayCode = (String) last.getBody().get("code");
    }
  }

  @Given("the client manager has issued a stay code for hotel {string} from {string} to {string}")
  public void theClientManagerHasIssuedAStayCode(String hotel, String from, String to) {
    theClientManagerIssuesAStayCode(hotel, from, to);
    assertThat(last.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Then("a stay code is issued with check-in {string}, check-out {string} and a discount of {int}%")
  public void aStayCodeIsIssued(String checkIn, String checkOut, int discount) {
    assertThat(last.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(last.getBody())
        .containsEntry("checkIn", date(checkIn))
        .containsEntry("checkOut", date(checkOut))
        .containsEntry("discountPercent", discount);
    assertThat((String) last.getBody().get("code")).isNotBlank();
  }

  @Then("a tourist quoted the {string} pass with that code pays {string}")
  public void aTouristQuotedThePassPays(String passTypeCode, String price) {
    ResponseEntity<Map<String, Object>> quote =
        tourist()
            .get(
                "/pass-types/"
                    + passType(passTypeCode).get("id")
                    + "/price?stayCode="
                    + lastStayCode);
    assertThat(quote.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(quote.getBody()).containsEntry("price", price);
  }

  @Then("the stay code is refused because of {word}")
  public void theStayCodeIsRefusedBecauseOf(String field) {
    assertThat(last.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(FleetApi.errors(last)).containsKey(field);
  }

  @Then("the hotel is refused because the name is already in use")
  public void theHotelIsRefusedBecauseTheNameIsInUse() {
    assertThat(last.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(FleetApi.errors(last)).containsKey("name");
  }

  // ------------------------------------------------------------- BR21, BR23: other roles

  @When("the {word} tries to edit {}")
  public void theRoleTriesToEdit(String role, String what) {
    FleetApi caller =
        switch (role) {
          case "operator" -> operator();
          case "tourist" -> tourist();
          default -> throw new IllegalArgumentException("operator or tourist: " + role);
        };
    last =
        switch (what) {
          case "the rules of use" -> caller.send(HttpMethod.PUT, "/usage-rules", validRules());
          case "a deposit" ->
              caller.send(HttpMethod.PATCH, "/vehicle-types/E_BIKE", Map.of("depositAmount", "5"));
          case "a pass type" ->
              caller.send(
                  HttpMethod.POST,
                  "/pass-types",
                  Map.of(
                      "code", "CUKE_X",
                      "name", "Sneaky",
                      "validityDays", 1,
                      "price", "1.00",
                      "vehicleTypes", List.of("E_BIKE")));
          case "a stay code" ->
              caller.send(
                  HttpMethod.POST,
                  "/stay-codes",
                  Map.of(
                      "accommodationId", UUID.randomUUID(),
                      "checkIn", date("today"),
                      "checkOut", date("tomorrow")));
          default -> throw new IllegalArgumentException("Unknown edit: " + what);
        };
  }

  @Then("the edit is refused as forbidden")
  public void theEditIsRefusedAsForbidden() {
    assertThat(last.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
