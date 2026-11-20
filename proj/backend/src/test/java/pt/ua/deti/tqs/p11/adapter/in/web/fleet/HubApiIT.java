package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static pt.ua.deti.tqs.p11.support.FleetApi.errors;
import static pt.ua.deti.tqs.p11.support.FleetApi.id;

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

/** US08 (KAN-81): hubs through /hubs, as an operator. Who may call what is in AccessMatrixApiIT. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class HubApiIT {

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private FleetApi operator;

  /** Roles are set in the database and read on every request, so the token works right away. */
  @BeforeEach
  void loginAsOperatorWithAnEmptyFleet() {
    jdbc.update("DELETE FROM vehicle_state_changes");
    jdbc.update("DELETE FROM vehicles");
    jdbc.update("DELETE FROM hubs");
    jdbc.update("DELETE FROM users");
    RestClient http = ApiClient.forPort(port);
    Registered rui = ApiClient.registerGuest(http, "Rui Costa", "rui.costa@example.com", "Passw0rd!");
    jdbc.update("UPDATE users SET role = 'OPERATOR' WHERE id = ?", rui.id());
    operator = new FleetApi(http, rui.token());
  }

  private void fill(UUID hubId, int vehicles) {
    for (int i = 1; i <= vehicles; i++) {
      operator.bike("bike-%04d".formatted(i), hubId);
    }
  }

  // ------------------------------------------------------------- register

  @Test
  void register_validHub_returns201WithTheHubHoldingNoVehicles() {
    ResponseEntity<Map<String, Object>> response =
        operator.registerHub("hub-03", "Canal Central", 40.6405, -8.6538, 10);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody())
        .containsKey("id")
        .containsEntry("code", "hub-03")
        .containsEntry("name", "Canal Central")
        .containsEntry("latitude", 40.6405)
        .containsEntry("longitude", -8.6538)
        .containsEntry("capacity", 10)
        .containsEntry("vehiclesHeld", 0);
  }

  @Test
  void register_invalidFields_returns400NamingEachOne() {
    ResponseEntity<Map<String, Object>> response =
        operator.registerHub("hub/03", " ", 90.0001, -180.0001, 0);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response))
        .containsOnlyKeys("code", "name", "latitude", "longitude", "capacity");
  }

  @Test
  void register_missingFields_returns400NamingEachOne() {
    ResponseEntity<Map<String, Object>> response =
        operator.send(HttpMethod.POST, "/hubs", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response))
        .containsOnlyKeys("code", "name", "latitude", "longitude", "capacity");
  }

  @Test
  void register_boundaryValues_areAccepted() {
    ResponseEntity<Map<String, Object>> response =
        operator.registerHub("a".repeat(64), "Polo Norte", 90, -180, 1);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void register_codeAlreadyInUse_returns409OnTheCode() {
    operator.hub("hub-03", 10);

    ResponseEntity<Map<String, Object>> response =
        operator.registerHub("hub-03", "Fórum Aveiro", 40.6412, -8.6501, 5);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(response)).containsOnlyKeys("code");
  }

  // ------------------------------------------------------------- capacity (BR14)

  @Test
  void changeCapacity_lowering10To9With9Held_returns200WithTheNewCapacity() {
    UUID hub = operator.hub("hub-03", 10);
    fill(hub, 9);

    ResponseEntity<Map<String, Object>> response = operator.changeCapacity(hub, 9);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("capacity", 9).containsEntry("vehiclesHeld", 9);
  }

  @Test
  void changeCapacity_lowering10To9With10Held_returns409OnTheCapacityAndKeeps10() {
    UUID hub = operator.hub("hub-03", 10);
    fill(hub, 10);

    ResponseEntity<Map<String, Object>> response = operator.changeCapacity(hub, 9);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(errors(response)).containsOnlyKeys("capacity");
    assertThat(operator.get("/hubs/" + hub).getBody()).containsEntry("capacity", 10);
  }

  @Test
  void changeCapacity_belowOne_returns400() {
    UUID hub = operator.hub("hub-03", 10);

    ResponseEntity<Map<String, Object>> response = operator.changeCapacity(hub, 0);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsOnlyKeys("capacity");
  }

  @Test
  void changeCapacity_unknownHub_returns404() {
    ResponseEntity<Map<String, Object>> response = operator.changeCapacity(UUID.randomUUID(), 9);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    // Our problem body, not the 404 of a path that has no endpoint.
    assertThat(response.getBody()).containsKey("errors");
  }

  // ------------------------------------------------------------- read

  @Test
  void list_returnsEveryHubOrderedByCodeWithTheVehiclesItHolds() {
    UUID hub05 = operator.hub("hub-05", 12);
    operator.hub("hub-03", 10);
    fill(hub05, 2);

    ResponseEntity<List<Map<String, Object>>> response = operator.getList("/hubs");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .extracting(hub -> hub.get("code"), hub -> hub.get("vehiclesHeld"))
        .containsExactly(
            tuple("hub-03", 0),
            tuple("hub-05", 2));
  }

  @Test
  void get_returnsTheHub() {
    UUID hub = id(operator.registerHub("hub-03", "Canal Central", 40.6405, -8.6538, 10));

    ResponseEntity<Map<String, Object>> response = operator.get("/hubs/" + hub);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("code", "hub-03")
        .containsEntry("capacity", 10)
        .containsEntry("vehiclesHeld", 0);
  }

  @Test
  void get_unknownHub_returns404() {
    ResponseEntity<Map<String, Object>> response = operator.get("/hubs/" + UUID.randomUUID());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsKey("errors");
  }
}
