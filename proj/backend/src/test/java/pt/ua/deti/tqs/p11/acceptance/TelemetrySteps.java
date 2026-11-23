package pt.ua.deti.tqs.p11.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static pt.ua.deti.tqs.p11.support.TelemetryMessages.FLEET;
import static pt.ua.deti.tqs.p11.support.TelemetryMessages.reading;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.adapter.in.telemetry.MqttTelemetrySubscriber;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;
import pt.ua.deti.tqs.p11.support.FleetApi;

/**
 * Steps for US09 (KAN-61), run against the real application, database and MQTT broker. Readings are
 * published as the simulator does. A discard is shown with a marker reading for another vehicle
 * that is applied afterwards: messages are handled in order, so the earlier one is done by then.
 */
public class TelemetrySteps {

  private static final Duration WITHIN_SLO = Duration.ofSeconds(5);
  private static final AtomicInteger MARKERS = new AtomicInteger();

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private MqttTelemetrySubscriber subscriber;

  private FleetApi operator;
  private FleetApi tourist;

  /** Messages published before the subscription exists would be delivered out of order. */
  @Before
  public void waitUntilTheAdapterListens() throws Exception {
    subscriber.subscribed().get(10, TimeUnit.SECONDS);
  }

  // ------------------------------------------------------------- helpers

  private Map<String, Object> vehicle(String code) {
    return operator.getList("/vehicles").getBody().stream()
        .filter(found -> code.equals(found.get("code")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No vehicle " + code));
  }

  private void publish(String code, double battery, String lockState, String timestamp) {
    CucumberSpringConfiguration.BROKER.publishTelemetry(
        FLEET, code, reading(code, Double.toString(battery), lockState, timestamp));
  }

  private void awaitBattery(String code, int percent) {
    await()
        .atMost(WITHIN_SLO)
        .untilAsserted(() -> assertThat(vehicle(code).get("batteryPercent")).isEqualTo(percent));
  }

  // ------------------------------------------------------------- setup

  @Given("an operator watches the fleet")
  public void anOperatorWatchesTheFleet() {
    RestClient http = ApiClient.forPort(port);
    Registered sofia =
        ApiClient.registerGuest(http, "Sofia Ramos", "sofia.ramos@example.com", "Passw0rd!");
    jdbc.update("UPDATE users SET role = 'OPERATOR' WHERE id = ?", sofia.id());
    operator = new FleetApi(http, sofia.token());
  }

  @Given(
      "the simulator has published a reading for {string} with battery {double}%, lock state {string} at {string}")
  public void theSimulatorHasPublished(String code, double battery, String lockState, String at) {
    publish(code, battery, lockState, at);
    awaitBattery(code, (int) Math.floor(battery));
  }

  // ------------------------------------------------------------- publishing

  @When(
      "the simulator publishes a reading for {string} with battery {double}%, lock state {string} at {string}")
  public void theSimulatorPublishes(String code, double battery, String lockState, String at) {
    publish(code, battery, lockState, at);
  }

  @When("the simulator publishes a message that is not valid telemetry for {string}")
  public void theSimulatorPublishesGarbage(String code) {
    CucumberSpringConfiguration.BROKER.publishTelemetry(
        FLEET, code, reading(code, "50", "broken", "2026-11-10T10:00:00Z"));
    CucumberSpringConfiguration.BROKER.publishTelemetry(FLEET, code, "this is not json");
  }

  // ------------------------------------------------------------- outcome

  /** Publishes a marker reading for a vehicle of its own and waits until it is applied. */
  @Then("the reading is discarded")
  public void theReadingIsDiscarded() {
    String marker = "marker-%d".formatted(MARKERS.incrementAndGet());
    UUID hub = jdbc.queryForObject("SELECT id FROM hubs WHERE code = 'hub-03'", UUID.class);
    jdbc.update(
        "INSERT INTO vehicles (id, code, type, state, hub_id) "
            + "VALUES (?, ?, 'E_BIKE', 'AVAILABLE', ?)",
        UUID.randomUUID(),
        marker,
        hub);
    publish(marker, 50, "locked", "2026-11-10T10:00:00Z");
    awaitBattery(marker, 50);
  }

  @Then("vehicle {string} shows a battery of {int}%")
  public void vehicleShowsABattery(String code, int percent) {
    awaitBattery(code, percent);
  }

  @Then("vehicle {string} shows lock state {string} and position {double}, {double}")
  public void vehicleShowsLockStateAndPosition(String code, String lock, double lat, double lon) {
    assertThat(vehicle(code))
        .containsEntry("lockState", lock)
        .containsEntry("latitude", lat)
        .containsEntry("longitude", lon);
  }

  @Then("vehicle {string} shows no battery reading")
  public void vehicleShowsNoBatteryReading(String code) {
    assertThat(vehicle(code)).containsEntry("batteryPercent", null);
  }

  // ------------------------------------------------------------- the search sees it (US02)

  private boolean bookable(String hubCode, String code) {
    if (tourist == null) {
      RestClient http = ApiClient.forPort(port);
      Registered ana =
          ApiClient.registerGuest(http, "Ana Lopes", "ana.lopes@example.com", "Passw0rd!");
      tourist = new FleetApi(http, ana.token());
    }
    UUID hub =
        UUID.fromString(
            (String)
                operator.getList("/hubs").getBody().stream()
                    .filter(found -> hubCode.equals(found.get("code")))
                    .findFirst()
                    .orElseThrow()
                    .get("id"));
    List<Map<String, Object>> found = tourist.getList("/hubs/" + hub + "/vehicles").getBody();
    return found.stream()
        .filter(vehicle -> code.equals(vehicle.get("code")))
        .map(vehicle -> (Boolean) vehicle.get("bookable"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Vehicle %s is not in the search".formatted(code)));
  }

  @Given("a tourist searching hub {string} sees vehicle {string} as bookable")
  public void aTouristSeesBookable(String hubCode, String code) {
    assertThat(bookable(hubCode, code)).isTrue();
  }

  @Then("a tourist searching hub {string} sees vehicle {string} as not bookable")
  public void aTouristSeesNotBookable(String hubCode, String code) {
    await().atMost(WITHIN_SLO).untilAsserted(() -> assertThat(bookable(hubCode, code)).isFalse());
  }
}
