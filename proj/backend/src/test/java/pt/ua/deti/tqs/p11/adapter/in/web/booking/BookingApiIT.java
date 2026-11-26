package pt.ua.deti.tqs.p11.adapter.in.web.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;
import pt.ua.deti.tqs.p11.support.FleetApi;
import pt.ua.deti.tqs.p11.support.PaymentGatewayContainer;

/**
 * POST /bookings for an immediate start (US04) over HTTP, with a real database and the simulated
 * gateway in a container. The rules are tested in the domain and use case tests; here the status
 * codes, the body, the Location and the error field of each refusal (decision D4).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class BookingApiIT {

  private static final PaymentGatewayContainer GATEWAY = new PaymentGatewayContainer();
  private static final UUID DAY_ID = UUID.fromString("7a1e0000-0000-4000-8000-000000000001");
  private static final String EMAILS = "'%.bookingapi@example.com'";
  private static final String CODES = "'bkapi-%'";

  @DynamicPropertySource
  static void gateway(DynamicPropertyRegistry registry) {
    GATEWAY.start();
    registry.add("app.payment.base-url", GATEWAY::baseUrl);
    registry.add("app.payment.read-timeout", () -> "PT1S");
  }

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private RestClient http;
  private FleetApi operator;
  private Registered ana;
  private UUID hub;
  private UUID bike;

  /**
   * Before and after each test: the context is shared with tests that delete every user, so no row
   * may point to an account, vehicle or hub of this test when it ends. Children first.
   */
  @AfterEach
  void removeTheRowsOfThisTest() {
    String mine = "(SELECT id FROM users WHERE email LIKE " + EMAILS + ")";
    String vehicles = "(SELECT id FROM vehicles WHERE code LIKE " + CODES + ")";
    jdbc.update("DELETE FROM payments WHERE account_id IN " + mine);
    jdbc.update("DELETE FROM bookings WHERE account_id IN " + mine);
    jdbc.update("DELETE FROM passes WHERE account_id IN " + mine);
    jdbc.update("DELETE FROM vehicle_state_changes WHERE vehicle_id IN " + vehicles);
    jdbc.update("DELETE FROM vehicles WHERE code LIKE " + CODES);
    jdbc.update("DELETE FROM hubs WHERE code LIKE " + CODES);
    jdbc.update("DELETE FROM users WHERE email LIKE " + EMAILS);
  }

  @BeforeEach
  void aTouristWithAPassAndAnAvailableBike() {
    removeTheRowsOfThisTest();
    http = ApiClient.forPort(port);
    Registered ops =
        ApiClient.registerGuest(http, "Ops Tester", "ops.bookingapi@example.com", "Passw0rd!");
    jdbc.update("UPDATE users SET role = 'OPERATOR' WHERE id = ?", ops.id());
    operator = new FleetApi(http, ops.token());
    hub = operator.hub("bkapi-hub", 10);
    bike = availableVehicle("bkapi-bike-1", "E_BIKE");
    ana = ApiClient.registerGuest(http, "Ana Tester", "ana.bookingapi@example.com", "Passw0rd!");
    buyADayPass(ana);
  }

  // ------------------------------------------------------------- helpers

  private UUID availableVehicle(String code, String type) {
    UUID id = FleetApi.id(FleetApi.created(operator.registerVehicle(code, type, hub)));
    assertThat(operator.transition(id, "ACTIVATE").getStatusCode())
        .as("setup: activate %s", code)
        .isEqualTo(HttpStatus.OK);
    jdbc.update("UPDATE vehicles SET battery_percent = 100 WHERE id = ?", id);
    return id;
  }

  private void buyADayPass(Registered who) {
    ResponseEntity<Map<String, Object>> bought =
        http.post()
            .uri("/passes")
            .header("Authorization", "Bearer " + who.token())
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("passTypeId", DAY_ID, "paymentToken", "tok_approve"))
            .retrieve()
            .toEntity(ApiClient.JSON_OBJECT);
    assertThat(bought.getStatusCode()).as("setup: buy a day pass").isEqualTo(HttpStatus.CREATED);
  }

  private ResponseEntity<Map<String, Object>> start(String token, Map<String, Object> body) {
    return http.post()
        .uri("/bookings")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  private ResponseEntity<Map<String, Object>> start(
      Registered who, UUID vehicle, int minutes, String card) {
    Map<String, Object> body = new HashMap<>();
    body.put("vehicleId", vehicle);
    body.put("plannedEnd", Instant.now().plus(Duration.ofMinutes(minutes)).toString());
    body.put("paymentToken", card);
    return start(who.token(), body);
  }

  private ResponseEntity<Map<String, Object>> startBike(int minutes) {
    return start(ana, bike, minutes, "tok_approve");
  }

  private static void assertRefusedOn(
      ResponseEntity<Map<String, Object>> response, HttpStatus status, String field) {
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(FleetApi.errors(response)).containsKey(field);
  }

  // ------------------------------------------------------------- a valid start

  @Test
  void start_validRequest_is201WithTheActiveBookingAndItsLocation() {
    ResponseEntity<Map<String, Object>> response = startBike(60);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Map<String, Object> body = response.getBody();
    assertThat(body)
        .containsEntry("state", "ACTIVE")
        .containsEntry("vehicleId", bike.toString())
        .containsEntry("startHubId", hub.toString())
        .containsEntry("deposit", "30.00")
        .containsEntry("currency", "EUR")
        .containsKeys("id", "passId", "plannedStart", "plannedEnd", "startedAt");
    assertThat(response.getHeaders().getLocation())
        .hasToString("/bookings/" + body.get("id"));
  }

  @Test
  void start_validRequest_putsTheVehicleInUse() {
    startBike(60);

    assertThat(operator.get("/vehicles/" + bike).getBody()).containsEntry("state", "IN_USE");
  }

  @Test
  void start_scooter_holdsTheScooterDeposit() {
    UUID scooter = availableVehicle("bkapi-scooter-1", "E_SCOOTER");

    ResponseEntity<Map<String, Object>> response = start(ana, scooter, 60, "tok_approve");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsEntry("deposit", "50.00");
  }

  // ------------------------------------------------------------- refusals (D4)

  @Test
  void start_durationAboveTheMaximum_is400OnPlannedEnd() {
    assertRefusedOn(startBike(241), HttpStatus.BAD_REQUEST, "plannedEnd");
  }

  @Test
  void start_vehicleNotAvailable_is409OnVehicleId() {
    UUID deactivated =
        FleetApi.id(FleetApi.created(operator.registerVehicle("bkapi-bike-2", "E_BIKE", hub)));

    assertRefusedOn(
        start(ana, deactivated, 60, "tok_approve"), HttpStatus.CONFLICT, "vehicleId");
  }

  @Test
  void start_withoutAValidPass_is409OnPass() {
    Registered rui =
        ApiClient.registerGuest(http, "Rui Tester", "rui.bookingapi@example.com", "Passw0rd!");

    assertRefusedOn(start(rui, bike, 60, "tok_approve"), HttpStatus.CONFLICT, "pass");
  }

  @Test
  void start_whileRidingAnotherVehicle_is409OnBooking() {
    UUID other = availableVehicle("bkapi-bike-3", "E_BIKE");
    assertThat(start(ana, other, 30, "tok_approve").getStatusCode())
        .as("setup: first ride")
        .isEqualTo(HttpStatus.CREATED);

    assertRefusedOn(startBike(60), HttpStatus.CONFLICT, "booking");
  }

  @Test
  void start_depositDeclined_is402OnDepositAndTheVehicleStaysAvailable() {
    ResponseEntity<Map<String, Object>> response = start(ana, bike, 60, "tok_decline");

    assertRefusedOn(response, HttpStatus.PAYMENT_REQUIRED, "deposit");
    assertThat(operator.get("/vehicles/" + bike).getBody()).containsEntry("state", "AVAILABLE");
  }

  @Test
  void start_depositTimedOut_is504OnDepositAndTheVehicleStaysAvailable() {
    ResponseEntity<Map<String, Object>> response = start(ana, bike, 60, "tok_timeout");

    assertRefusedOn(response, HttpStatus.GATEWAY_TIMEOUT, "deposit");
    assertThat(operator.get("/vehicles/" + bike).getBody()).containsEntry("state", "AVAILABLE");
  }

  @Test
  void start_unknownVehicle_is404() {
    assertThat(start(ana, UUID.randomUUID(), 60, "tok_approve").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void start_missingFields_is400OnEachOfThem() {
    ResponseEntity<Map<String, Object>> response = start(ana.token(), Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(FleetApi.errors(response)).containsKeys("vehicleId", "plannedEnd", "paymentToken");
  }

  // ------------------------------------------------------------- access (BR23)

  /** The access matrix: only tourists (and admins) start a ride. */
  @Test
  void start_asAnOperator_is403() {
    Map<String, Object> body = new HashMap<>();
    body.put("vehicleId", bike);
    body.put("plannedEnd", Instant.now().plus(Duration.ofMinutes(60)).toString());
    body.put("paymentToken", "tok_approve");
    String operatorToken =
        ApiClient.registerGuest(http, "Op Two", "op2.bookingapi@example.com", "Passw0rd!").token();
    jdbc.update(
        "UPDATE users SET role = 'OPERATOR' WHERE email = 'op2.bookingapi@example.com'");

    assertThat(start(operatorToken, body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}