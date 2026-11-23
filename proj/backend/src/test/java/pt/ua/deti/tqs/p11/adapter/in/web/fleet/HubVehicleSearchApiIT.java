package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;
import pt.ua.deti.tqs.p11.support.FleetApi;

/**
 * US02 (KAN-91): searching the vehicles of a hub through GET /hubs/{id}/vehicles, as a tourist. The
 * fleet is set up through the operator API. Battery levels come from telemetry, which the backend
 * does not receive yet (KAN-92), so they are written to the database as a telemetry update would.
 * Who may call it is in AccessMatrixApiIT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class HubVehicleSearchApiIT {

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private FleetApi operator;
  private FleetApi tourist;
  private UUID hub03;

  @BeforeEach
  void startWithAnEmptyFleet() {
    jdbc.update("DELETE FROM vehicle_state_changes");
    jdbc.update("DELETE FROM vehicles");
    jdbc.update("DELETE FROM hubs");
    jdbc.update("DELETE FROM users");
    RestClient http = ApiClient.forPort(port);
    Registered rui =
        ApiClient.registerGuest(http, "Rui Costa", "rui.costa@example.com", "Passw0rd!");
    jdbc.update("UPDATE users SET role = 'OPERATOR' WHERE id = ?", rui.id());
    operator = new FleetApi(http, rui.token());
    Registered ana =
        ApiClient.registerGuest(http, "Ana Lopes", "ana.lopes@example.com", "Passw0rd!");
    tourist = new FleetApi(http, ana.token());
    hub03 = operator.hub("hub-03", 10);
  }

  private UUID availableVehicle(String code, String type, UUID hub, Integer battery) {
    UUID id = FleetApi.id(FleetApi.created(operator.registerVehicle(code, type, hub)));
    operator.transition(id, "ACTIVATE");
    setBattery(id, battery);
    return id;
  }

  /** What a telemetry update does to the vehicle. */
  private void setBattery(UUID vehicleId, Integer percent) {
    jdbc.update("UPDATE vehicles SET battery_percent = ? WHERE id = ?", percent, vehicleId);
  }

  private List<Map<String, Object>> search(UUID hub, String query) {
    ResponseEntity<List<Map<String, Object>>> response =
        tourist.getList("/hubs/" + hub + "/vehicles" + query);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private static Map<String, Object> vehicleWithCode(List<Map<String, Object>> list, String code) {
    return list.stream()
        .filter(vehicle -> code.equals(vehicle.get("code")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No vehicle " + code + " in " + list));
  }

  // ------------------------------------------------------------- what the tourist sees

  @Test
  void search_hubWithVehicles_listsThemWithTypeStateBatteryAndBookability() {
    UUID bike = availableVehicle("bike-0001", "E_BIKE", hub03, 80);

    List<Map<String, Object>> result = search(hub03, "");

    assertThat(result).hasSize(1);
    assertThat(result.getFirst())
        .containsEntry("id", bike.toString())
        .containsEntry("code", "bike-0001")
        .containsEntry("type", "E_BIKE")
        .containsEntry("state", "AVAILABLE")
        .containsEntry("batteryPercent", 80)
        .containsEntry("bookable", true)
        .containsEntry("unbookableReason", null);
  }

  @Test
  void search_listsOnlyTheVehiclesOfThatHubOrderedByCode() {
    UUID hub05 = operator.hub("hub-05", 10);
    availableVehicle("bike-0002", "E_BIKE", hub03, 80);
    availableVehicle("bike-0001", "E_BIKE", hub03, 80);
    availableVehicle("bike-0003", "E_BIKE", hub05, 80);

    assertThat(search(hub03, ""))
        .extracting(vehicle -> vehicle.get("code"))
        .containsExactly("bike-0001", "bike-0002");
  }

  // ------------------------------------------------------------- type filter

  @Test
  void search_withType_listsOnlyVehiclesOfThatType() {
    availableVehicle("bike-0001", "E_BIKE", hub03, 80);
    availableVehicle("scooter-0001", "E_SCOOTER", hub03, 80);

    assertThat(search(hub03, "?type=E_SCOOTER"))
        .extracting(vehicle -> vehicle.get("code"))
        .containsExactly("scooter-0001");
    assertThat(search(hub03, "?type=E_BIKE"))
        .extracting(vehicle -> vehicle.get("code"))
        .containsExactly("bike-0001");
  }

  @Test
  void search_withoutType_listsEveryType() {
    availableVehicle("bike-0001", "E_BIKE", hub03, 80);
    availableVehicle("scooter-0001", "E_SCOOTER", hub03, 80);

    assertThat(search(hub03, "")).hasSize(2);
  }

  @Test
  void search_unknownType_returns400() {
    ResponseEntity<Map<String, Object>> response =
        tourist.get("/hubs/" + hub03 + "/vehicles?type=E_CAR");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // ------------------------------------------------------------- BR1: minimum battery

  /** 19% is refused and 20% accepted, with the default minimum of 20%. */
  @Test
  void search_batteryAtTheMinimum_isBookableAndJustBelowIsNot() {
    availableVehicle("bike-0019", "E_BIKE", hub03, 19);
    availableVehicle("bike-0020", "E_BIKE", hub03, 20);

    List<Map<String, Object>> result = search(hub03, "");

    assertThat(vehicleWithCode(result, "bike-0019"))
        .containsEntry("bookable", false)
        .containsEntry("unbookableReason", "LOW_BATTERY");
    assertThat(vehicleWithCode(result, "bike-0020"))
        .containsEntry("bookable", true)
        .containsEntry("unbookableReason", null);
  }

  /** A telemetry update changes the next search, not only the first one. */
  @Test
  void search_afterATelemetryUpdate_showsTheNewBattery() {
    UUID bike = availableVehicle("bike-0001", "E_BIKE", hub03, 80);
    assertThat(vehicleWithCode(search(hub03, ""), "bike-0001")).containsEntry("bookable", true);

    setBattery(bike, 19);
    assertThat(vehicleWithCode(search(hub03, ""), "bike-0001"))
        .containsEntry("batteryPercent", 19)
        .containsEntry("bookable", false)
        .containsEntry("unbookableReason", "LOW_BATTERY");

    setBattery(bike, 20);
    assertThat(vehicleWithCode(search(hub03, ""), "bike-0001"))
        .containsEntry("batteryPercent", 20)
        .containsEntry("bookable", true);
  }

  @Test
  void search_vehicleWithoutABatteryReading_isNotBookable() {
    availableVehicle("bike-0001", "E_BIKE", hub03, null);

    assertThat(search(hub03, "").getFirst())
        .containsEntry("batteryPercent", null)
        .containsEntry("bookable", false)
        .containsEntry("unbookableReason", "NO_BATTERY_READING");
  }

  // ------------------------------------------------------------- BR2: only Available

  @Test
  void search_vehicleNotAvailable_isListedButNotBookable() {
    UUID deactivated = operator.bike("bike-0001", hub03);
    setBattery(deactivated, 100);
    UUID reserved = availableVehicle("bike-0002", "E_BIKE", hub03, 100);
    jdbc.update("UPDATE vehicles SET state = 'RESERVED' WHERE id = ?", reserved);
    UUID maintenance = availableVehicle("bike-0003", "E_BIKE", hub03, 100);
    jdbc.update("UPDATE vehicles SET state = 'MAINTENANCE' WHERE id = ?", maintenance);

    List<Map<String, Object>> result = search(hub03, "");

    assertThat(result)
        .hasSize(3)
        .allSatisfy(vehicle -> {
          assertThat(vehicle).containsEntry("bookable", false);
          assertThat(vehicle).containsEntry("unbookableReason", "NOT_AVAILABLE");
        });
    assertThat(vehicleWithCode(result, "bike-0002")).containsEntry("state", "RESERVED");
  }

  @Test
  void search_vehicleInUseOrInTransit_isNotListedBecauseItIsNotAtTheHub() {
    UUID moving = availableVehicle("bike-0001", "E_BIKE", hub03, 100);
    operator.transition(moving, "START_MOVE");

    assertThat(search(hub03, "")).isEmpty();
  }

  // ------------------------------------------------------------- BR1 with a duration

  @Test
  void search_withoutMinutes_requiresOnlyTheFloor() {
    availableVehicle("bike-0001", "E_BIKE", hub03, 80);

    assertThat(search(hub03, "").getFirst()).containsEntry("requiredBatteryPercent", 20);
  }

  /** 20% floor + 5% per started 30 minutes: 60 minutes need 30%, 61 minutes need 35%. */
  @Test
  void search_withMinutes_needsTheFloorPlusTheRidesShare() {
    availableVehicle("bike-0029", "E_BIKE", hub03, 29);
    availableVehicle("bike-0030", "E_BIKE", hub03, 30);

    List<Map<String, Object>> sixty = search(hub03, "?minutes=60");

    assertThat(vehicleWithCode(sixty, "bike-0029"))
        .containsEntry("requiredBatteryPercent", 30)
        .containsEntry("bookable", false)
        .containsEntry("unbookableReason", "INSUFFICIENT_FOR_DURATION");
    assertThat(vehicleWithCode(sixty, "bike-0030"))
        .containsEntry("requiredBatteryPercent", 30)
        .containsEntry("bookable", true);
    assertThat(vehicleWithCode(search(hub03, "?minutes=61"), "bike-0030"))
        .containsEntry("requiredBatteryPercent", 35)
        .containsEntry("bookable", false);
  }

  @Test
  void search_withMinutesAndType_appliesBoth() {
    availableVehicle("bike-0001", "E_BIKE", hub03, 80);
    availableVehicle("scooter-0001", "E_SCOOTER", hub03, 80);

    assertThat(search(hub03, "?type=E_SCOOTER&minutes=30"))
        .extracting(vehicle -> vehicle.get("code"))
        .containsExactly("scooter-0001");
  }

  @Test
  void search_aRideNoBatteryCanDo_leavesEveryVehicleNotBookable() {
    availableVehicle("bike-0001", "E_BIKE", hub03, 100);

    assertThat(search(hub03, "?minutes=1440").getFirst())
        .containsEntry("bookable", false)
        .containsEntry("unbookableReason", "INSUFFICIENT_FOR_DURATION");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-5", "1441", "abc", "2.5"})
  void search_minutesOutsideOneToADay_returns400NamingTheField(String minutes) {
    ResponseEntity<Map<String, Object>> response =
        tourist.get("/hubs/" + hub03 + "/vehicles?minutes=" + minutes);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void search_minutesOutOfRange_namesTheMinutesField() {
    ResponseEntity<Map<String, Object>> response =
        tourist.get("/hubs/" + hub03 + "/vehicles?minutes=1441");

    assertThat(FleetApi.errors(response)).containsKey("minutes");
  }

  // ------------------------------------------------------------- empty and unknown

  @Test
  void search_hubWithNoVehicle_returns200AndAnEmptyList() {
    assertThat(search(hub03, "")).isEmpty();
  }

  @Test
  void search_unknownHub_returns404() {
    ResponseEntity<Map<String, Object>> response =
        tourist.get("/hubs/" + UUID.randomUUID() + "/vehicles");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
