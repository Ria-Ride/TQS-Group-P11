package pt.ua.deti.tqs.p11.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;
import pt.ua.deti.tqs.p11.support.FleetApi;

/**
 * Steps for US04 (KAN-56), run against the real API, database, MQTT broker and simulated payment
 * gateway. Tourists log in with PassPurchaseSteps; hubs and vehicles are set up by
 * VehicleSearchSteps, and vehicle states checked by FleetSteps. Passes are written to the database
 * with the validity each scenario needs, from a fixed "now" taken at the start of the scenario, so
 * the boundaries (4 h, the end of the Pass) do not depend on how fast the steps run.
 */
public class BookingSteps {

  private static final Map<String, String> CARDS =
      Map.of("approved", "tok_approve", "declined", "tok_decline", "timed out", "tok_timeout");
  private static final String RIDE_OVERLAP = "already has a booking in that period";

  /** Receives every unlock, as the simulator would; shared by all scenarios. */
  private static Mqtt5BlockingClient.Mqtt5Publishes unlocks;

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PassPurchaseSteps passSteps;

  private RestClient http;
  private Instant now;
  private ResponseEntity<Map<String, Object>> lastStart;
  private final List<Registered> riders = new ArrayList<>();
  private List<ResponseEntity<Map<String, Object>>> race = List.of();

  /**
   * Runs before every other cleaning hook: deposits point to bookings, and bookings to passes
   * (order 9000), vehicles, hubs and users (default order). Unlocks left from earlier scenarios are
   * dropped.
   */
  @Before(order = 8000)
  public void startWithNoBookings() {
    jdbc.update("DELETE FROM payments WHERE booking_id IS NOT NULL");
    jdbc.update("DELETE FROM bookings");
    http = ApiClient.forPort(port);
    now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Mqtt5BlockingClient.Mqtt5Publishes received = unlocks();
    Optional<Mqtt5Publish> old = received.receiveNow();
    while (old.isPresent()) {
      old = received.receiveNow();
    }
  }

  /** After the passes are deleted (order 9000), before the users. */
  @Before(order = 9500)
  public void removeTheTestPassTypes() {
    jdbc.update(
        "DELETE FROM pass_type_vehicle_types WHERE pass_type_id IN"
            + " (SELECT id FROM pass_types WHERE code LIKE 'TEST_%')");
    jdbc.update("DELETE FROM pass_types WHERE code LIKE 'TEST_%'");
  }

  // ------------------------------------------------------------- helpers

  private static synchronized Mqtt5BlockingClient.Mqtt5Publishes unlocks() {
    if (unlocks == null) {
      Mqtt5BlockingClient listener =
          Mqtt5Client.builder()
              .identifier("unlock-listener-" + UUID.randomUUID())
              .serverHost(CucumberSpringConfiguration.BROKER.host())
              .serverPort(CucumberSpringConfiguration.BROKER.port())
              .buildBlocking();
      listener.connect();
      unlocks = listener.publishes(MqttGlobalPublishFilter.ALL);
      listener
          .subscribeWith()
          .topicFilter("fleet/+/vehicles/+/commands/unlock")
          .qos(MqttQos.AT_LEAST_ONCE)
          .send();
    }
    return unlocks;
  }

  private Registered tourist(String name) {
    return passSteps.tourist(name);
  }

  private UUID idOf(String table, String code) {
    return jdbc.queryForObject("SELECT id FROM " + table + " WHERE code = ?", UUID.class, code);
  }

  private void insertPass(UUID account, UUID passType, Instant from, Instant to) {
    jdbc.update(
        "INSERT INTO passes (id, account_id, pass_type_id, price, valid_from, valid_to)"
            + " VALUES (?, ?, ?, 10.00, ?, ?)",
        UUID.randomUUID(),
        account,
        passType,
        Timestamp.from(from),
        Timestamp.from(to));
  }

  /** A day pass type that allows only one vehicle type; the seeded ones allow both. */
  private UUID passTypeAllowingOnly(String vehicleType) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO pass_types (id, code, name, validity_days, base_price)"
            + " VALUES (?, ?, 'Test pass', 1, 10.00)",
        id,
        "TEST_" + vehicleType);
    jdbc.update(
        "INSERT INTO pass_type_vehicle_types (pass_type_id, vehicle_type) VALUES (?, ?)",
        id,
        vehicleType);
    return id;
  }

  private ResponseEntity<Map<String, Object>> start(
      String token, String vehicle, int minutes, String card) {
    return http.post()
        .uri("/bookings")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "vehicleId", idOf("vehicles", vehicle),
                "plannedEnd", now.plus(Duration.ofMinutes(minutes)).toString(),
                "paymentToken", card))
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  private int count(String sql, Object... args) {
    Integer found = jdbc.queryForObject(sql, Integer.class, args);
    return found == null ? 0 : found;
  }

  /** "InUse" in the scenarios, "IN_USE" in the database. */
  private static String dbState(String state) {
    return state.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
  }

  /** How each refusal of the feature reaches the client (ApiExceptionHandler, decision D4). */
  private record Refusal(HttpStatus status, String field, String message) {}

  private static Refusal refusal(String reason) {
    if (reason.endsWith(RIDE_OVERLAP)) {
      return new Refusal(
          HttpStatus.CONFLICT, "booking", "you already have a booking in that period");
    }
    return switch (reason) {
      case "the duration is not allowed" ->
          new Refusal(
              HttpStatus.BAD_REQUEST,
              "plannedEnd",
              "must be after now and within the longest booking allowed");
      case "the ride ends after the pass" ->
          new Refusal(HttpStatus.BAD_REQUEST, "plannedEnd", "is after the end of your pass");
      case "the vehicle is not available" ->
          new Refusal(HttpStatus.CONFLICT, "vehicleId", "is not available");
      case "the battery is not enough" ->
          new Refusal(HttpStatus.CONFLICT, "vehicleId", "has not enough battery for this ride");
      case "there is no valid pass" ->
          new Refusal(HttpStatus.CONFLICT, "pass", "you have no valid pass");
      case "the pass does not allow the vehicle type" ->
          new Refusal(HttpStatus.CONFLICT, "pass", "your pass does not allow this vehicle type");
      case "the deposit was declined" ->
          new Refusal(HttpStatus.PAYMENT_REQUIRED, "deposit", "was declined");
      case "the deposit was timed out" ->
          new Refusal(HttpStatus.GATEWAY_TIMEOUT, "deposit", "got no answer in time; try again");
      default -> throw new IllegalArgumentException("Unknown refusal: " + reason);
    };
  }

  // ------------------------------------------------------------- Given

  @Given("{string} has a valid {string} pass")
  public void hasValidPass(String name, String passType) {
    insertPass(
        tourist(name).id(),
        idOf("pass_types", passType),
        now.minus(Duration.ofHours(1)),
        now.plus(Duration.ofHours(23)));
  }

  @Given("{string} has a valid pass that allows only {string}")
  public void hasValidPassAllowingOnly(String name, String vehicleType) {
    insertPass(
        tourist(name).id(),
        passTypeAllowingOnly(vehicleType),
        now.minus(Duration.ofHours(1)),
        now.plus(Duration.ofHours(23)));
  }

  @Given("{string} has a valid {string} pass that ends in {int} minutes")
  public void hasValidPassEndingIn(String name, String passType, int minutes) {
    insertPass(
        tourist(name).id(),
        idOf("pass_types", passType),
        now.minus(Duration.ofHours(23)),
        now.plus(Duration.ofMinutes(minutes)));
  }

  @Given("{string} has started using {string} for {int} minutes")
  public void hasStartedUsing(String name, String vehicle, int minutes) {
    assertThat(start(tourist(name).token(), vehicle, minutes, "tok_approve").getStatusCode())
        .as("setup: %s starts using %s", name, vehicle)
        .isEqualTo(HttpStatus.CREATED);
  }

  @Given("{int} tourists with a valid {string} pass")
  public void touristsWithValidPass(int count, String passType) {
    UUID type = idOf("pass_types", passType);
    for (int i = 1; i <= count; i++) {
      Registered rider =
          ApiClient.registerGuest(http, "Rider " + i, "rider" + i + "@example.com", "Passw0rd!");
      insertPass(rider.id(), type, now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(23)));
      riders.add(rider);
    }
  }

  // ------------------------------------------------------------- When

  @When("{string} starts using {string} for {int} minutes")
  public void startsUsing(String name, String vehicle, int minutes) {
    lastStart = start(tourist(name).token(), vehicle, minutes, "tok_approve");
  }

  @When("{string} starts using {string} for {int} minutes and the deposit is {outcome}")
  public void startsUsingWithDepositOutcome(
      String name, String vehicle, int minutes, String outcome) {
    lastStart = start(tourist(name).token(), vehicle, minutes, CARDS.get(outcome));
  }

  /** BR3: every rider waits at the same latch, then all requests go out together. */
  @When("they all start using {string} for {int} minutes at the same time")
  public void allStartAtTheSameTime(String vehicle, int minutes) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(riders.size());
    try {
      CountDownLatch go = new CountDownLatch(1);
      List<Future<ResponseEntity<Map<String, Object>>>> sent = new ArrayList<>();
      for (Registered rider : riders) {
        sent.add(
            pool.submit(() -> {
              go.await();
              return start(rider.token(), vehicle, minutes, "tok_approve");
            }));
      }
      go.countDown();
      List<ResponseEntity<Map<String, Object>>> answers = new ArrayList<>();
      for (Future<ResponseEntity<Map<String, Object>>> answer : sent) {
        answers.add(answer.get(30, TimeUnit.SECONDS));
      }
      race = answers;
    } finally {
      pool.shutdownNow();
    }
  }

  // ------------------------------------------------------------- Then

  @Then("{string} has an Active booking of {string} starting at hub {string}")
  public void hasActiveBooking(String name, String vehicle, String hub) {
    assertThat(lastStart.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(lastStart.getBody())
        .containsEntry("state", "ACTIVE")
        .containsEntry("vehicleId", idOf("vehicles", vehicle).toString())
        .containsEntry("startHubId", idOf("hubs", hub).toString());
    assertThat(
            count(
                "SELECT COUNT(*) FROM bookings WHERE account_id = ? AND vehicle_id = ?"
                    + " AND state = 'ACTIVE'",
                tourist(name).id(),
                idOf("vehicles", vehicle)))
        .isEqualTo(1);
  }

  @Then("a deposit of {bigdecimal} EUR is authorized for the booking")
  public void depositAuthorized(BigDecimal amount) {
    assertThat(lastStart.getBody()).containsEntry("deposit", amount.setScale(2).toPlainString());
    BigDecimal held =
        jdbc.queryForObject(
            "SELECT amount FROM payments WHERE booking_id = ? AND kind = 'DEPOSIT'"
                + " AND state = 'AUTHORIZED'",
            BigDecimal.class,
            UUID.fromString((String) lastStart.getBody().get("id")));
    assertThat(held).isEqualByComparingTo(amount);
  }

  @Then("the state history of {string} records the change from {word} to {word}")
  public void historyRecordsChange(String vehicle, String from, String to) {
    assertThat(
            count(
                "SELECT COUNT(*) FROM vehicle_state_changes WHERE vehicle_id = ?"
                    + " AND from_state = ? AND to_state = ?",
                idOf("vehicles", vehicle),
                dbState(from),
                dbState(to)))
        .isEqualTo(1);
  }

  /** As the simulator would: a message on the vehicle's unlock topic. */
  @Then("vehicle {string} receives an unlock command")
  public void receivesUnlockCommand(String vehicle) throws InterruptedException {
    String topic = "/vehicles/" + vehicle + "/commands/unlock";
    Instant deadline = Instant.now().plusSeconds(5);
    while (Instant.now().isBefore(deadline)) {
      Optional<Mqtt5Publish> received = unlocks().receive(1, TimeUnit.SECONDS);
      if (received.isPresent() && received.get().getTopic().toString().endsWith(topic)) {
        return;
      }
    }
    throw new AssertionError("No unlock reached " + vehicle);
  }

  @Then("the start is accepted")
  public void startAccepted() {
    assertThat(lastStart.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Then("the start is refused because {}")
  public void startRefused(String reason) {
    Refusal expected = refusal(reason);
    assertThat(lastStart.getStatusCode()).isEqualTo(expected.status());
    assertThat(FleetApi.errors(lastStart)).containsEntry(expected.field(), expected.message());
  }

  /** BR11: refused before any money was held, so no deposit at all. */
  @Then("no deposit is authorized for {string}")
  public void noDepositAuthorized(String name) {
    assertThat(
            count(
                "SELECT COUNT(*) FROM payments WHERE account_id = ? AND kind = 'DEPOSIT'",
                tourist(name).id()))
        .isZero();
  }

  @Then("{string} has no booking")
  public void hasNoBooking(String name) {
    assertThat(count("SELECT COUNT(*) FROM bookings WHERE account_id = ?", tourist(name).id()))
        .isZero();
  }

  @Then("exactly one of them has an Active booking of {string}")
  public void exactlyOneHasActiveBooking(String vehicle) {
    assertThat(race).filteredOn(r -> r.getStatusCode() == HttpStatus.CREATED).hasSize(1);
    assertThat(
            count(
                "SELECT COUNT(*) FROM bookings WHERE vehicle_id = ? AND state = 'ACTIVE'",
                idOf("vehicles", vehicle)))
        .isEqualTo(1);
  }

  @Then("the other {int} are refused because the vehicle is not available")
  public void othersRefused(int losers) {
    assertThat(race)
        .filteredOn(r -> r.getStatusCode() == HttpStatus.CONFLICT)
        .filteredOn(r -> FleetApi.errors(r).containsKey("vehicleId"))
        .hasSize(losers);
  }

  /**
   * BR3: only the winner keeps a held deposit. A loser that held one gave it back; a loser that
   * came after the winner's commit was refused before holding anything.
   */
  @Then("the deposit of every refused tourist is released")
  public void depositsOfLosersReleased() {
    int held = 0;
    for (Registered rider : riders) {
      held +=
          count(
              "SELECT COUNT(*) FROM payments WHERE account_id = ? AND kind = 'DEPOSIT'"
                  + " AND state = 'AUTHORIZED'",
              rider.id());
    }
    assertThat(held).isEqualTo(1);
  }
}
