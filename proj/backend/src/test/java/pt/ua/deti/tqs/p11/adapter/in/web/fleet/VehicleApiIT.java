package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.ua.deti.tqs.p11.support.FleetApi.errors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * US08 (KAN-82, KAN-83): vehicles through /vehicles, as an operator. The full table of allowed
 * transitions is in VehicleStateTest; here, how the API reports each outcome.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class VehicleApiIT {

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private FleetApi operator;
  private UUID hub03;
  private UUID hub05;

  @BeforeEach
  void loginAsOperatorWithTwoEmptyHubs() {
    jdbc.update("DELETE FROM vehicle_state_changes");
    jdbc.update("DELETE FROM vehicles");
    jdbc.update("DELETE FROM hubs");
    jdbc.update("DELETE FROM users");
    RestClient http = ApiClient.forPort(port);
    Registered rui =
        ApiClient.registerGuest(http, "Rui Costa", "rui.costa@example.com", "Passw0rd!");
    jdbc.update("UPDATE users SET role = 'OPERATOR' WHERE id = ?", rui.id());
    operator = new FleetApi(http, rui.token());
    hub03 = operator.hub("hub-03", 10);
    hub05 = operator.hub("hub-05", 10);
  }

  private void fill(UUID hubId, String prefix, int vehicles) {
    for (int i = 1; i <= vehicles; i++) {
      operator.bike(prefix + "-%04d".formatted(i), hubId);
    }
  }

  private UUID availableBike() {
    UUID bike = operator.bike("bike-0009", hub03);
    assertThat(operator.transition(bike, "ACTIVATE").getStatusCode()).isEqualTo(HttpStatus.OK);
    return bike;
  }

  private Map<String, Object> vehicle(UUID id) {
    return operator.get("/vehicles/" + id).getBody();
  }

  // ------------------------------------------------------------- register (BR14, BR16)

  @Test
  void register_inAHubWithAFreeSlot_returns201Deactivated() {
    fill(hub03, "scooter", 9);

    ResponseEntity<Map<String, Object>> response =
        operator.registerVehicle("bike-0009", "E_BIKE", hub03);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody())
        .containsKey("id")
        .containsEntry("code", "bike-0009")
        .containsEntry("type", "E_BIKE")
        .containsEntry("state", "DEACTIVATED")
        .containsEntry("hubId", hub03.toString());
  }

  @Test
  void register_inAFullHub_returns409OnTheHubAndCreatesNothing() {
    fill(hub03, "scooter", 10);

    ResponseEntity<Map<String, Object>> response =
        operator.registerVehicle("bike-0009", "E_BIKE", hub03);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(response)).containsOnlyKeys("hubId");
    assertThat(operator.getList("/vehicles").getBody()).hasSize(10);
  }

  @Test
  void register_invalidFields_returns400NamingEachOne() {
    ResponseEntity<Map<String, Object>> response =
        operator.send(HttpMethod.POST, "/vehicles", Map.of("code", "bike 0009"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsOnlyKeys("code", "type", "hubId");
  }

  @Test
  void register_unknownType_returns400() {
    ResponseEntity<Map<String, Object>> response =
        operator.registerVehicle("bike-0009", "E_CAR", hub03);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void register_codeAlreadyInUse_returns409OnTheCode() {
    operator.bike("bike-0009", hub03);

    ResponseEntity<Map<String, Object>> response =
        operator.registerVehicle("bike-0009", "E_SCOOTER", hub05);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(response)).containsOnlyKeys("code");
  }

  @Test
  void register_unknownHub_returns404() {
    ResponseEntity<Map<String, Object>> response =
        operator.registerVehicle("bike-0009", "E_BIKE", UUID.randomUUID());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsKey("errors");
  }

  // ------------------------------------------------------------- transitions (BR16)

  @Test
  void activate_returns200Available() {
    UUID bike = operator.bike("bike-0009", hub03);

    ResponseEntity<Map<String, Object>> response = operator.transition(bike, "ACTIVATE");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("state", "AVAILABLE")
        .containsEntry("hubId", hub03.toString());
  }

  @Test
  void deactivate_availableVehicle_returns200Deactivated() {
    UUID bike = availableBike();

    ResponseEntity<Map<String, Object>> response = operator.transition(bike, "DEACTIVATE");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("state", "DEACTIVATED");
  }

  /** Bookings do not exist yet (US05), so the vehicle is reserved in the database. */
  @Test
  void deactivate_reservedVehicle_returns409OnTheActionAndChangesNothing() {
    UUID bike = availableBike();
    jdbc.update("UPDATE vehicles SET state = 'RESERVED' WHERE id = ?", bike);

    ResponseEntity<Map<String, Object>> response = operator.transition(bike, "DEACTIVATE");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(response)).containsOnlyKeys("action");
    assertThat(vehicle(bike)).containsEntry("state", "RESERVED");
  }

  @Test
  void startMove_returns200InTransitWithNoHub() {
    UUID bike = availableBike();

    ResponseEntity<Map<String, Object>> response = operator.transition(bike, "START_MOVE");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("state", "IN_TRANSIT")
        .containsEntry("hubId", null);
  }

  @Test
  void confirmArrival_atAHubWithAFreeSlot_returns200AvailableThere() {
    UUID bike = availableBike();
    operator.transition(bike, "START_MOVE");

    ResponseEntity<Map<String, Object>> response =
        operator.transition(bike, "CONFIRM_ARRIVAL", hub05);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("state", "AVAILABLE")
        .containsEntry("hubId", hub05.toString());
  }

  @Test
  void confirmArrival_atAFullHub_returns409OnTheHubAndStaysInTransit() {
    UUID bike = availableBike();
    operator.transition(bike, "START_MOVE");
    fill(hub05, "scooter", 10);

    ResponseEntity<Map<String, Object>> response =
        operator.transition(bike, "CONFIRM_ARRIVAL", hub05);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(response)).containsOnlyKeys("hubId");
    assertThat(vehicle(bike)).containsEntry("state", "IN_TRANSIT");
  }

  @Test
  void confirmArrival_withoutAHub_returns400OnTheHub() {
    UUID bike = availableBike();
    operator.transition(bike, "START_MOVE");

    ResponseEntity<Map<String, Object>> response = operator.transition(bike, "CONFIRM_ARRIVAL");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsOnlyKeys("hubId");
  }

  /** A hub only makes sense for an arrival; sending one elsewhere is a client mistake. */
  @Test
  void otherAction_withAHub_returns400OnTheHub() {
    UUID bike = operator.bike("bike-0009", hub03);

    ResponseEntity<Map<String, Object>> response = operator.transition(bike, "ACTIVATE", hub05);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsOnlyKeys("hubId");
  }

  @Test
  void retire_returns200RetiredWithNoHub() {
    UUID bike = availableBike();

    ResponseEntity<Map<String, Object>> response = operator.transition(bike, "RETIRE");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("state", "RETIRED").containsEntry("hubId", null);
  }

  @Test
  void activate_retiredVehicle_returns409OnTheAction() {
    UUID bike = availableBike();
    operator.transition(bike, "RETIRE");

    ResponseEntity<Map<String, Object>> response = operator.transition(bike, "ACTIVATE");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(response)).containsOnlyKeys("action");
  }

  @Test
  void transition_withoutAction_returns400OnTheAction() {
    UUID bike = operator.bike("bike-0009", hub03);

    ResponseEntity<Map<String, Object>> response =
        operator.send(HttpMethod.POST, "/vehicles/" + bike + "/transitions", new HashMap<>());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsOnlyKeys("action");
  }

  @Test
  void transition_unknownVehicle_returns404() {
    ResponseEntity<Map<String, Object>> response =
        operator.transition(UUID.randomUUID(), "ACTIVATE");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsKey("errors");
  }

  // ------------------------------------------------------------- history and reads

  @Test
  void history_listsEveryAllowedTransitionInOrderAndNoRefusedOne() {
    UUID bike = availableBike();
    operator.transition(bike, "START_MOVE");
    operator.transition(bike, "ACTIVATE"); // refused: InTransit cannot be activated
    operator.transition(bike, "CONFIRM_ARRIVAL", hub05);
    operator.transition(bike, "RETIRE");

    ResponseEntity<List<Map<String, Object>>> response =
        operator.getList("/vehicles/" + bike + "/history");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .extracting(change -> change.get("from") + ">" + change.get("to"))
        .containsExactly(
            "null>DEACTIVATED",
            "DEACTIVATED>AVAILABLE",
            "AVAILABLE>IN_TRANSIT",
            "IN_TRANSIT>AVAILABLE",
            "AVAILABLE>RETIRED");
    assertThat(response.getBody()).allSatisfy(change -> assertThat(change.get("at")).isNotNull());
  }

  @Test
  void history_unknownVehicle_returns404() {
    ResponseEntity<Map<String, Object>> response =
        operator.get("/vehicles/" + UUID.randomUUID() + "/history");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void list_returnsEveryVehicleOrderedByCode() {
    operator.bike("bike-0010", hub05);
    operator.bike("bike-0009", hub03);

    ResponseEntity<List<Map<String, Object>>> response = operator.getList("/vehicles");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .extracting(vehicle -> vehicle.get("code"))
        .containsExactly("bike-0009", "bike-0010");
  }

  @Test
  void get_unknownVehicle_returns404() {
    ResponseEntity<Map<String, Object>> response = operator.get("/vehicles/" + UUID.randomUUID());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsKey("errors");
  }
}
