package pt.ua.deti.tqs.p11.adapter.in.web.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
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
import pt.ua.deti.tqs.p11.application.rules.RulesOfUseRepository;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;
import pt.ua.deti.tqs.p11.support.FleetApi;
import pt.ua.deti.tqs.p11.support.PaymentGatewayContainer;
import pt.ua.deti.tqs.p11.support.TestRules;

/**
 * POST /bookings for an immediate start (US04) over HTTP, with a real database and the simulated
 * gateway in a container. The rules are tested in the domain and use case tests; here the status
 * codes, the body, the Location and the error field of each refusal (decision D4), and simultaneous
 * requests against the real database locks (BR3, BR6; KAN-702).
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
    registry.add("app.payment.read-timeout", () -> PaymentGatewayContainer.READ_TIMEOUT.toString());
  }

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private RulesOfUseRepository rulesOfUse;

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
    jdbc.update("DELETE FROM rules_of_use_vehicle_types WHERE version > 1");
    jdbc.update("DELETE FROM rules_of_use WHERE version > 1");
  }

  @BeforeEach
  void aTouristWithAPassAndAnAvailableBike() {
    removeTheRowsOfThisTest();
    // The rules in force are those the tests are written with (4 h, 30.00/50.00), not the seeded
    // version 1 that the client manager may change (US14).
    rulesOfUse.save(TestRules.bookingRules(2, Instant.now()));
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
    assertThat(response.getHeaders().getLocation()).hasToString("/bookings/" + body.get("id"));
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

    assertRefusedOn(start(ana, deactivated, 60, "tok_approve"), HttpStatus.CONFLICT, "vehicleId");
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
    jdbc.update("UPDATE users SET role = 'OPERATOR' WHERE email = 'op2.bookingapi@example.com'");

    assertThat(start(operatorToken, body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  private static final String RIDERS = "'rider%.bookingapi@example.com'";

  private List<Registered> ridersWithADayPass(int count) {
    List<Registered> riders = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      Registered rider =
          ApiClient.registerGuest(
              http, "Rider " + i, "rider" + i + ".bookingapi@example.com", "Passw0rd!");
      buyADayPass(rider);
      riders.add(rider);
    }
    return riders;
  }

  /** Every request waits at one latch, then all go out together, each on its own thread. */
  private static List<ResponseEntity<Map<String, Object>>> allAtOnce(
      List<Callable<ResponseEntity<Map<String, Object>>>> requests) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(requests.size());
    try {
      CountDownLatch go = new CountDownLatch(1);
      List<Future<ResponseEntity<Map<String, Object>>>> sent = new ArrayList<>();
      for (Callable<ResponseEntity<Map<String, Object>>> request : requests) {
        sent.add(
            pool.submit(() -> {
              go.await();
              return request.call();
            }));
      }
      go.countDown();
      List<ResponseEntity<Map<String, Object>>> answers = new ArrayList<>();
      for (Future<ResponseEntity<Map<String, Object>>> answer : sent) {
        answers.add(answer.get(30, TimeUnit.SECONDS));
      }
      return answers;
    } finally {
      pool.shutdownNow();
    }
  }

  private int count(String sql, Object... args) {
    Integer found = jdbc.queryForObject(sql, Integer.class, args);
    return found == null ? 0 : found;
  }

  /**
   * BR3: of n tourists asking for the same bike at once, exactly one gets it; every other one is
   * told the vehicle is not available, and only the winner keeps a held deposit.
   */
  private void raceForTheBike(int tourists) throws Exception {
    List<Callable<ResponseEntity<Map<String, Object>>>> requests = new ArrayList<>();
    for (Registered rider : ridersWithADayPass(tourists)) {
      requests.add(() -> start(rider, bike, 60, "tok_approve"));
    }

    List<ResponseEntity<Map<String, Object>>> answers = allAtOnce(requests);

    assertThat(answers).filteredOn(a -> a.getStatusCode() == HttpStatus.CREATED).hasSize(1);
    assertThat(answers)
        .filteredOn(a -> a.getStatusCode() == HttpStatus.CONFLICT)
        .allSatisfy(a -> assertThat(FleetApi.errors(a)).containsKey("vehicleId"))
        .hasSize(tourists - 1);
    assertThat(
            count("SELECT COUNT(*) FROM bookings WHERE vehicle_id = ? AND state = 'ACTIVE'", bike))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT COUNT(*) FROM payments p JOIN users u ON u.id = p.account_id"
                    + " WHERE u.email LIKE "
                    + RIDERS
                    + " AND p.kind = 'DEPOSIT' AND p.state = 'AUTHORIZED'"))
        .as("only the winner keeps a held deposit")
        .isEqualTo(1);
    assertThat(operator.get("/vehicles/" + bike).getBody()).containsEntry("state", "IN_USE");
  }

  @Test
  void twoSimultaneousStarts_exactlyOneGetsTheBike() throws Exception {
    raceForTheBike(2);
  }

  /** Repeated on every build, so a flaky lock shows up in CI and not in the demo. */
  @RepeatedTest(5)
  void tenSimultaneousStarts_exactlyOneGetsTheBike() throws Exception {
    raceForTheBike(10);
  }

  /** BR6, D2: one tourist asks for two vehicles at once; the account lock lets one ride through. */
  @Test
  void oneTouristStartingTwoVehiclesAtOnce_ridesOnlyOne() throws Exception {
    UUID other = availableVehicle("bkapi-bike-4", "E_BIKE");

    List<ResponseEntity<Map<String, Object>>> answers =
        allAtOnce(
            List.of(
                () -> start(ana, bike, 60, "tok_approve"),
                () -> start(ana, other, 60, "tok_approve")));

    assertThat(answers).filteredOn(a -> a.getStatusCode() == HttpStatus.CREATED).hasSize(1);
    assertThat(answers)
        .filteredOn(a -> a.getStatusCode() == HttpStatus.CONFLICT)
        .singleElement()
        .satisfies(a -> assertThat(FleetApi.errors(a)).containsKey("booking"));
    assertThat(
            count(
                "SELECT COUNT(*) FROM bookings WHERE account_id = ? AND state = 'ACTIVE'",
                ana.id()))
        .isEqualTo(1);
  }

  // ------------------------------------------------------------- US05: reservations

  private ResponseEntity<Map<String, Object>> reserveBike(Registered who, int from, int to) {
    Instant now = Instant.now();
    Map<String, Object> body = new HashMap<>();
    body.put("vehicleId", bike);
    body.put("plannedStart", now.plus(Duration.ofMinutes(from)).toString());
    body.put("plannedEnd", now.plus(Duration.ofMinutes(to)).toString());
    body.put("paymentToken", "tok_approve");
    return start(who.token(), body);
  }

  private ResponseEntity<Map<String, Object>> act(Registered who, Object bookingId, String action) {
    return http.post()
        .uri("/bookings/{id}/{action}", bookingId, action)
        .header("Authorization", "Bearer " + who.token())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of())
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  private ResponseEntity<Map<String, Object>> read(Registered who, Object bookingId) {
    return http.get()
        .uri("/bookings/{id}", bookingId)
        .header("Authorization", "Bearer " + who.token())
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  private Registered bruno() {
    return ApiClient.registerGuest(
        http, "Bruno Tester", "bruno.bookingapi@example.com", "Passw0rd!");
  }

  @Test
  void reserve_validRequest_is201WithTheReservedBookingItsWindowAndItsLocation() {
    ResponseEntity<Map<String, Object>> response = reserveBike(ana, 60, 120);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Map<String, Object> body = response.getBody();
    assertThat(body)
        .containsEntry("state", "RESERVED")
        .containsEntry("startedAt", null)
        .containsEntry("deposit", "30.00");
    Instant plannedStart = Instant.parse((String) body.get("plannedStart"));
    assertThat(Instant.parse((String) body.get("startWindowEndsAt")))
        .isEqualTo(plannedStart.plus(Duration.ofMinutes(15)));
    assertThat(response.getHeaders().getLocation()).hasPath("/bookings/" + body.get("id"));
  }

  @Test
  void reserve_startInThePast_is400OnPlannedStart() {
    assertRefusedOn(reserveBike(ana, -1, 60), HttpStatus.BAD_REQUEST, "plannedStart");
  }

  @Test
  void start_tooEarly_is409OnBooking() {
    Object id = reserveBike(ana, 60, 120).getBody().get("id");

    assertRefusedOn(act(ana, id, "start"), HttpStatus.CONFLICT, "booking");
  }

  @Test
  void cancel_ownReservation_is200WithTheCancelledBooking() {
    Object id = reserveBike(ana, 60, 120).getBody().get("id");

    ResponseEntity<Map<String, Object>> response = act(ana, id, "cancel");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("state", "CANCELLED")
        .containsEntry("cancelReason", "cancelled by the tourist");
  }

  @Test
  void cancel_activeBooking_is409OnBooking() {
    Object id = startBike(60).getBody().get("id");

    assertRefusedOn(act(ana, id, "cancel"), HttpStatus.CONFLICT, "booking");
  }

  /** BR23: another tourist's booking can be neither read, started nor cancelled. */
  @Test
  void anotherTouristsBooking_is403ForEveryAction() {
    Object id = reserveBike(ana, 60, 120).getBody().get("id");
    Registered bruno = bruno();

    assertThat(read(bruno, id).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(act(bruno, id, "start").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(act(bruno, id, "cancel").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(read(ana, id).getBody()).containsEntry("state", "RESERVED");
  }

  @Test
  void unknownBooking_is404() {
    assertThat(act(ana, UUID.randomUUID(), "cancel").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(read(ana, UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void list_returnsOnlyTheCallersBookings() {
    Object id = reserveBike(ana, 60, 120).getBody().get("id");
    Registered bruno = bruno();

    ResponseEntity<List<Map<String, Object>>> mine =
        http.get()
            .uri("/bookings")
            .header("Authorization", "Bearer " + ana.token())
            .retrieve()
            .toEntity(FleetApi.JSON_LIST);
    ResponseEntity<List<Map<String, Object>>> theirs =
        http.get()
            .uri("/bookings")
            .header("Authorization", "Bearer " + bruno.token())
            .retrieve()
            .toEntity(FleetApi.JSON_LIST);

    assertThat(mine.getBody()).extracting(booking -> booking.get("id")).containsExactly(id);
    assertThat(theirs.getBody()).isEmpty();
  }

  // ------------------------------------------------------------- US06: the end of a ride

  private ResponseEntity<Map<String, Object>> end(Registered who, Object bookingId, Object hubId) {
    Map<String, Object> body = new HashMap<>();
    body.put("hubId", hubId);
    return http.post()
        .uri("/bookings/{id}/end", bookingId)
        .header("Authorization", "Bearer " + who.token())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  /** Telemetry is off here: the lock is taken as done (LoggingVehicleCommands). */
  @Test
  void end_activeRideAtAHubWithRoom_is200WithTheSettlement() {
    Object id = startBike(60).getBody().get("id");

    ResponseEntity<Map<String, Object>> response = end(ana, id, hub);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("state", "COMPLETED")
        .containsEntry("endHubId", hub.toString())
        .containsEntry("overtimeCharge", "0.00")
        .containsEntry("depositCaptured", "0.00")
        .containsEntry("depositReleased", "30.00");
    assertThat(response.getBody().get("endedAt")).isNotNull();
  }

  @Test
  void end_atAFullHub_is409OnHubIdWithTheNearbyHubsWithRoom() {
    UUID full = operator.hub("bkapi-full", 1);
    operator.bike("bkapi-filler", full);
    Object id = startBike(60).getBody().get("id");

    ResponseEntity<Map<String, Object>> response = end(ana, id, full);

    assertRefusedOn(response, HttpStatus.CONFLICT, "hubId");
    List<Object> nearby =
        ((List<?>) response.getBody().get("nearbyHubs"))
            .stream().map(h -> (Object) ((Map<?, ?>) h).get("code")).toList();
    assertThat(nearby).contains("bkapi-hub").doesNotContain("bkapi-full");
    assertThat(read(ana, id).getBody())
        .containsEntry("state", "ACTIVE")
        .extractingByKey("fullHubAttemptAt")
        .isNotNull();
  }

  @Test
  void end_reservedBooking_is409OnBooking() {
    Object id = reserveBike(ana, 60, 120).getBody().get("id");

    ResponseEntity<Map<String, Object>> response = end(ana, id, hub);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(FleetApi.errors(response)).containsEntry("booking", "is not Active");
  }

  @Test
  void end_anotherTouristsRide_is403() {
    Object id = startBike(60).getBody().get("id");

    assertThat(end(bruno(), id, hub).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void end_withoutAHub_is400OnHubId() {
    Object id = startBike(60).getBody().get("id");

    assertRefusedOn(end(ana, id, null), HttpStatus.BAD_REQUEST, "hubId");
  }
}
