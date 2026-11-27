package pt.ua.deti.tqs.p11.adapter.in.web.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * The rules of use and the deposits over HTTP (US14, BR21, BR23), as the client manager and as
 * other roles. The limits of every value are tested in RulesOfUseTest; here the status codes, the
 * error fields, the versions and the effect on the vehicle search. Every test leaves version 1 as
 * the only version, because the rest of the suite starts from it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RulesOfUseApiIT {

  private static final String EMAILS = "'%.rulesapi@example.com'";

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private FleetApi manager;
  private FleetApi operator;
  private FleetApi tourist;
  private UUID managerId;

  @BeforeEach
  void startFromVersionOneWithThreeAccounts() {
    removeWhatThisTestMade();
    RestClient http = ApiClient.forPort(port);
    Registered mara =
        ApiClient.registerGuest(http, "Mara Silva", "mara.rulesapi@example.com", "Passw0rd!");
    jdbc.update("UPDATE users SET role = 'CLIENT_MANAGER' WHERE id = ?", mara.id());
    managerId = mara.id();
    manager = new FleetApi(http, mara.token());
    Registered rui =
        ApiClient.registerGuest(http, "Rui Costa", "rui.rulesapi@example.com", "Passw0rd!");
    jdbc.update("UPDATE users SET role = 'OPERATOR' WHERE id = ?", rui.id());
    operator = new FleetApi(http, rui.token());
    Registered ana =
        ApiClient.registerGuest(http, "Ana Lopes", "ana.rulesapi@example.com", "Passw0rd!");
    tourist = new FleetApi(http, ana.token());
  }

  @AfterEach
  void removeWhatThisTestMade() {
    jdbc.update("DELETE FROM rules_of_use_vehicle_types WHERE version > 1");
    jdbc.update("DELETE FROM rules_of_use WHERE version > 1");
    jdbc.update("DELETE FROM vehicle_state_changes");
    jdbc.update("DELETE FROM vehicles");
    jdbc.update("DELETE FROM hubs");
    jdbc.update("DELETE FROM users WHERE email LIKE " + EMAILS);
  }

  private static Map<String, Object> change() {
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

  private ResponseEntity<Map<String, Object>> put(FleetApi caller, Map<String, Object> body) {
    return caller.send(HttpMethod.PUT, "/usage-rules", body);
  }

  // ------------------------------------------------------------- reading

  @Test
  void get_anyLoggedInRole_readsTheCurrentRules() {
    ResponseEntity<Map<String, Object>> response = tourist.get("/usage-rules");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("version", 1)
        .containsEntry("maxBookingMinutes", 480)
        .containsEntry("minBatteryPercent", 20)
        .containsEntry("startWindowMinutes", 15)
        .containsEntry("repositioningWindowMinutes", 10)
        .containsEntry("overtimeGraceMinutes", 5)
        .containsEntry("overtimeBlockMinutes", 15)
        .containsEntry("overtimeRatePerBlock", "1.50")
        .containsEntry("currency", "EUR")
        .containsEntry("createdBy", null);
  }

  // ------------------------------------------------------------- BR21: a change is a new version

  @Test
  void put_asClientManager_makesVersionTwoAndIsRead_back() {
    ResponseEntity<Map<String, Object>> response = put(manager, change());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("version", 2)
        .containsEntry("createdBy", managerId.toString())
        .containsEntry("maxBookingMinutes", 240)
        .containsEntry("minBatteryPercent", 25)
        .containsEntry("startWindowMinutes", 20)
        .containsEntry("repositioningWindowMinutes", 12)
        .containsEntry("overtimeGraceMinutes", 0)
        .containsEntry("overtimeBlockMinutes", 30)
        .containsEntry("overtimeRatePerBlock", "2.00")
        .containsKey("createdAt");
    assertThat(tourist.get("/usage-rules").getBody())
        .containsEntry("version", 2)
        .containsEntry("minBatteryPercent", 25);
  }

  @Test
  void put_keepsTheOldVersion_asItWas() {
    put(manager, change());

    List<Map<String, Object>> versions = manager.getList("/usage-rules/versions").getBody();

    assertThat(versions).extracting(version -> version.get("version")).containsExactly(2, 1);
    assertThat(versions.get(1))
        .containsEntry("maxBookingMinutes", 480)
        .containsEntry("minBatteryPercent", 20);
    assertThat(versions.get(0)).containsEntry("maxBookingMinutes", 240);
  }

  @Test
  void put_keepsTheDeposits() {
    put(manager, change());

    assertThat(tourist.getList("/vehicle-types").getBody())
        .extracting(type -> type.get("depositAmount"))
        .containsExactlyInAnyOrder("20.00", "30.00");
  }

  /** BR21 limits: 1 to 1440 minutes for the longest booking, 0 to 100% of battery. */
  @ParameterizedTest(name = "{0} = {1} is refused")
  @CsvSource({
    "maxBookingMinutes, 0",
    "maxBookingMinutes, 1441",
    "maxBookingMinutes, -5",
    "minBatteryPercent, -1",
    "minBatteryPercent, 101",
    "startWindowMinutes, 0",
    "startWindowMinutes, 1441",
    "repositioningWindowMinutes, 0",
    "overtimeGraceMinutes, -1",
    "overtimeGraceMinutes, 1441",
    "overtimeBlockMinutes, 0",
    "overtimeBlockMinutes, 1441"
  })
  void put_valueOutsideItsLimits_returns400NamingTheField(String field, int value) {
    Map<String, Object> body = change();
    body.put(field, value);

    ResponseEntity<Map<String, Object>> response = put(manager, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(FleetApi.errors(response)).containsKey(field);
    assertThat(tourist.get("/usage-rules").getBody()).containsEntry("version", 1);
  }

  @Test
  void put_limitsThemselves_areAccepted() {
    Map<String, Object> body = change();
    body.put("maxBookingMinutes", 1440);
    body.put("minBatteryPercent", 100);
    body.put("overtimeGraceMinutes", 1440);

    assertThat(put(manager, body).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void put_batteryRateOutsideZeroToHundred_orForAMissingType_returns400() {
    Map<String, Object> tooHigh = change();
    tooHigh.put("batteryPercentPer30Minutes", Map.of("E_BIKE", 101, "E_SCOOTER", 9));
    Map<String, Object> missing = change();
    missing.put("batteryPercentPer30Minutes", Map.of("E_BIKE", 4));

    assertThat(FleetApi.errors(put(manager, tooHigh))).containsKey("batteryPercentPer30Minutes");
    assertThat(FleetApi.errors(put(manager, missing))).containsKey("batteryPercentPer30Minutes");
  }

  @Test
  void put_missingFieldsAndBadMoney_return400() {
    Map<String, Object> body = change();
    body.remove("maxBookingMinutes");
    body.put("overtimeRatePerBlock", "-1");

    ResponseEntity<Map<String, Object>> response = put(manager, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(FleetApi.errors(response)).containsKeys("maxBookingMinutes", "overtimeRatePerBlock");
  }

  // ------------------------------------------------------------- BR21, BR23: other roles

  @Test
  void put_otherRoles_areRefused_andNothingChanges() {
    assertThat(put(tourist, change()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(put(operator, change()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(manager.get("/usage-rules").getBody()).containsEntry("version", 1);
  }

  @Test
  void versions_otherRoles_areRefused() {
    assertThat(tourist.get("/usage-rules/versions").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(operator.get("/usage-rules/versions").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  // ------------------------------------------------------------- deposits per vehicle type

  @Test
  void vehicleTypes_listTheDepositAndTheBatteryRateOfEachType() {
    List<Map<String, Object>> types = tourist.getList("/vehicle-types").getBody();

    assertThat(types)
        .extracting(type -> type.get("type"))
        .containsExactlyInAnyOrder("E_BIKE", "E_SCOOTER");
    assertThat(types)
        .filteredOn(type -> "E_SCOOTER".equals(type.get("type")))
        .singleElement()
        .satisfies(type ->
            assertThat(type)
                .containsEntry("depositAmount", "30.00")
                .containsEntry("currency", "EUR")
                .containsEntry("batteryPercentPer30Minutes", 5));
  }

  @Test
  void patchVehicleType_changesTheDeposit_asANewVersionThatKeepsTheRest() {
    ResponseEntity<Map<String, Object>> response =
        manager.send(
            HttpMethod.PATCH, "/vehicle-types/E_SCOOTER", Map.of("depositAmount", "45.00"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("type", "E_SCOOTER")
        .containsEntry("depositAmount", "45.00");
    assertThat(tourist.get("/usage-rules").getBody())
        .containsEntry("version", 2)
        .containsEntry("createdBy", managerId.toString())
        .containsEntry("minBatteryPercent", 20);
    assertThat(tourist.getList("/vehicle-types").getBody())
        .extracting(type -> type.get("depositAmount"))
        .containsExactlyInAnyOrder("20.00", "45.00");
  }

  @ParameterizedTest(name = "deposit {0} is refused")
  @CsvSource({"-1", "10.123", "abc", "100000000000"})
  void patchVehicleType_invalidDeposit_returns400OnTheField(String deposit) {
    ResponseEntity<Map<String, Object>> response =
        manager.send(HttpMethod.PATCH, "/vehicle-types/E_BIKE", Map.of("depositAmount", deposit));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(FleetApi.errors(response)).containsKey("depositAmount");
  }

  @Test
  void patchVehicleType_zeroDeposit_isAccepted_andUnknownTypeAndOtherRolesAreRefused() {
    assertThat(
            manager
                .send(HttpMethod.PATCH, "/vehicle-types/E_BIKE", Map.of("depositAmount", "0"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            manager
                .send(HttpMethod.PATCH, "/vehicle-types/E_CAR", Map.of("depositAmount", "5"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            tourist
                .send(HttpMethod.PATCH, "/vehicle-types/E_BIKE", Map.of("depositAmount", "5"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            operator
                .send(HttpMethod.PATCH, "/vehicle-types/E_BIKE", Map.of("depositAmount", "5"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  // ------------------------------------------------------------- the vehicle search follows them

  /** BR1: the search reads the current rules, so a change reaches the next search. */
  @Test
  void aChangeOfTheMinimumBattery_reachesTheNextVehicleSearch() {
    UUID hub = operator.hub("hub-03", 10);
    UUID bike = operator.bike("bike-0001", hub);
    operator.transition(bike, "ACTIVATE");
    jdbc.update("UPDATE vehicles SET battery_percent = 40 WHERE id = ?", bike);
    assertThat(tourist.getList("/hubs/" + hub + "/vehicles").getBody().getFirst())
        .containsEntry("bookable", true)
        .containsEntry("requiredBatteryPercent", 20);

    Map<String, Object> stricter = change();
    stricter.put("minBatteryPercent", 50);
    put(manager, stricter);

    assertThat(tourist.getList("/hubs/" + hub + "/vehicles").getBody().getFirst())
        .containsEntry("bookable", false)
        .containsEntry("unbookableReason", "LOW_BATTERY")
        .containsEntry("requiredBatteryPercent", 50);
  }

  @Test
  void aChangeOfTheBatteryRate_reachesTheNextVehicleSearchWithADuration() {
    UUID hub = operator.hub("hub-03", 10);
    UUID bike = operator.bike("bike-0001", hub);
    operator.transition(bike, "ACTIVATE");
    jdbc.update("UPDATE vehicles SET battery_percent = 28 WHERE id = ?", bike);
    // 20% + 5% for 30 minutes = 25%: bookable.
    assertThat(tourist.getList("/hubs/" + hub + "/vehicles?minutes=30").getBody().getFirst())
        .containsEntry("bookable", true);

    Map<String, Object> hungrier = change();
    hungrier.put("minBatteryPercent", 20);
    hungrier.put("batteryPercentPer30Minutes", Map.of("E_BIKE", 10, "E_SCOOTER", 10));
    put(manager, hungrier);

    // 20% + 10% = 30% for 30 minutes: 28% is no longer enough.
    assertThat(tourist.getList("/hubs/" + hub + "/vehicles?minutes=30").getBody().getFirst())
        .containsEntry("bookable", false)
        .containsEntry("unbookableReason", "INSUFFICIENT_FOR_DURATION")
        .containsEntry("requiredBatteryPercent", 30);
  }
}
