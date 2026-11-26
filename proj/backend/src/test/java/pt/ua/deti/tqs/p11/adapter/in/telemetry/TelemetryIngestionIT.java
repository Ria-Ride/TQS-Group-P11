package pt.ua.deti.tqs.p11.adapter.in.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static pt.ua.deti.tqs.p11.support.TelemetryMessages.FLEET;
import static pt.ua.deti.tqs.p11.support.TelemetryMessages.reading;
import static pt.ua.deti.tqs.p11.support.TelemetryMessages.readingWithFault;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.support.MosquittoBroker;

/**
 * US09 end to end, from a real MQTT broker to the database: readings published as the simulator
 * does (QoS 1, retained) update the vehicle, and the ones BR17 forbids are discarded. A discard is
 * shown by publishing a marker reading for another vehicle afterwards: the adapter handles messages
 * in order, so when the marker is applied the earlier message has been handled.
 */
@SpringBootTest(properties = "app.telemetry.enabled=true")
@Import(TestcontainersConfiguration.class)
class TelemetryIngestionIT {

  private static final MosquittoBroker BROKER = new MosquittoBroker();

  @Autowired private JdbcTemplate jdbc;

  @BeforeAll
  static void startBroker() {
    BROKER.start();
  }

  @AfterAll
  static void stopBroker() {
    BROKER.stop();
  }

  @DynamicPropertySource
  static void brokerAddress(DynamicPropertyRegistry registry) {
    // Suppliers: read when the context starts, which is after @BeforeAll has started the broker.
    registry.add("app.telemetry.host", BROKER::host);
    registry.add("app.telemetry.port", BROKER::port);
  }

  /**
   * A fault reading opens a ticket (US10). Other tests delete every vehicle, so no ticket may point
   * to one when this test ends.
   */
  @AfterEach
  void removeTheTickets() {
    jdbc.update("DELETE FROM maintenance_tickets");
  }

  @BeforeEach
  void startWithAnEmptyFleet() {
    removeTheTickets();
    jdbc.update("DELETE FROM vehicle_state_changes");
    jdbc.update("DELETE FROM vehicles");
    jdbc.update("DELETE FROM hubs");
    jdbc.update(
        "INSERT INTO hubs (id, code, name, latitude, longitude, capacity) "
            + "VALUES (?, 'hub-03', 'Canal Central', 40.6405, -8.6538, 500)",
        HUB_ID);
  }

  private static final UUID HUB_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000103");

  private void vehicle(String code) {
    jdbc.update(
        "INSERT INTO vehicles (id, code, type, state, hub_id) "
            + "VALUES (?, ?, 'E_BIKE', 'AVAILABLE', ?)",
        UUID.randomUUID(),
        code,
        HUB_ID);
  }

  private Map<String, Object> row(String code) {
    return jdbc.queryForMap(
        "SELECT battery_percent, lock_state, latitude, longitude, last_reading_at "
            + "FROM vehicles WHERE code = ?",
        code);
  }

  private void awaitBattery(String code, int percent) {
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(row(code).get("battery_percent")).isEqualTo(percent));
  }

  // ------------------------------------------------------------- applied

  @Test
  void validReading_updatesBatteryPositionAndLockState() {
    vehicle("bike-0001");

    BROKER.publishTelemetry(
        FLEET, "bike-0001", reading("bike-0001", "78.5", "unlocked", "2026-11-10T10:00:00Z"));

    awaitBattery("bike-0001", 78);
    assertThat(row("bike-0001"))
        .containsEntry("lock_state", "UNLOCKED")
        .containsEntry("latitude", 40.6405)
        .containsEntry("longitude", -8.6538);
  }

  @Test
  void newerReading_replacesTheOlderOne() {
    vehicle("bike-0001");

    BROKER.publishTelemetry(FLEET, "bike-0001", reading("bike-0001", "80", "2026-11-10T10:00:00Z"));
    BROKER.publishTelemetry(FLEET, "bike-0001", reading("bike-0001", "79", "2026-11-10T10:00:10Z"));

    awaitBattery("bike-0001", 79);
  }

  // ------------------------------------------------------------- faults (US10)

  private List<Map<String, Object>> ticketsOf(String code) {
    return jdbc.queryForList(
        "SELECT t.fault_code, t.state FROM maintenance_tickets t"
            + " JOIN vehicles v ON v.id = t.vehicle_id WHERE v.code = ? ORDER BY t.fault_code",
        code);
  }

  private String stateOf(String code) {
    return jdbc.queryForObject("SELECT state FROM vehicles WHERE code = ?", String.class, code);
  }

  /** BR18, BR19: through the real broker, as the simulator publishes it. */
  @Test
  void readingWithAFault_opensATicketAndSendsTheVehicleToMaintenance() {
    vehicle("bike-0001");

    BROKER.publishTelemetry(
        FLEET,
        "bike-0001",
        readingWithFault("bike-0001", "78", "BRAKE_WEAR", "2026-11-10T10:00:00Z"));

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() ->
            assertThat(ticketsOf("bike-0001"))
                .containsExactly(Map.of("fault_code", "BRAKE_WEAR", "state", "OPEN")));
    assertThat(stateOf("bike-0001")).isEqualTo("MAINTENANCE");
  }

  /** BR20: the simulator repeats the fault while it lasts; the second reading opens nothing. */
  @Test
  void repeatedReadingsOfTheSameFault_keepOneTicket() {
    vehicle("bike-0001");

    BROKER.publishTelemetry(
        FLEET,
        "bike-0001",
        readingWithFault("bike-0001", "80", "BRAKE_WEAR", "2026-11-10T10:00:00Z"));
    BROKER.publishTelemetry(
        FLEET,
        "bike-0001",
        readingWithFault("bike-0001", "79", "BRAKE_WEAR", "2026-11-10T10:00:10Z"));

    awaitBattery("bike-0001", 79);
    assertThat(ticketsOf("bike-0001"))
        .containsExactly(Map.of("fault_code", "BRAKE_WEAR", "state", "OPEN"));
  }

  @Test
  void readingWithoutAFault_opensNoTicket() {
    vehicle("bike-0001");

    BROKER.publishTelemetry(FLEET, "bike-0001", reading("bike-0001", "78", "2026-11-10T10:00:00Z"));

    awaitBattery("bike-0001", 78);
    assertThat(ticketsOf("bike-0001")).isEmpty();
    assertThat(stateOf("bike-0001")).isEqualTo("AVAILABLE");
  }

  // ------------------------------------------------------------- BR17: discarded

  @Test
  void batteryOfMinusOneOr101_isDiscarded() {
    vehicle("bike-0001");
    vehicle("bike-0002");
    vehicle("marker");

    BROKER.publishTelemetry(FLEET, "bike-0001", reading("bike-0001", "-1", "2026-11-10T10:00:00Z"));
    BROKER.publishTelemetry(
        FLEET, "bike-0002", reading("bike-0002", "101", "2026-11-10T10:00:00Z"));
    BROKER.publishTelemetry(FLEET, "marker", reading("marker", "50", "2026-11-10T10:00:00Z"));

    awaitBattery("marker", 50);
    assertThat(row("bike-0001").get("battery_percent")).isNull();
    assertThat(row("bike-0002").get("battery_percent")).isNull();
  }

  @Test
  void readingNotNewerThanTheLastOne_isDiscarded() {
    vehicle("bike-0001");
    vehicle("marker");
    BROKER.publishTelemetry(FLEET, "bike-0001", reading("bike-0001", "80", "2026-11-10T10:00:10Z"));
    awaitBattery("bike-0001", 80);

    BROKER.publishTelemetry(FLEET, "bike-0001", reading("bike-0001", "10", "2026-11-10T10:00:10Z"));
    BROKER.publishTelemetry(FLEET, "bike-0001", reading("bike-0001", "10", "2026-11-10T10:00:00Z"));
    BROKER.publishTelemetry(FLEET, "marker", reading("marker", "50", "2026-11-10T10:00:00Z"));

    awaitBattery("marker", 50);
    assertThat(row("bike-0001").get("battery_percent")).isEqualTo(80);
  }

  @Test
  void unknownVehicle_isDiscardedAndTheAdapterKeepsWorking() {
    vehicle("marker");

    BROKER.publishTelemetry(
        FLEET, "ghost-0001", reading("ghost-0001", "50", "2026-11-10T10:00:00Z"));
    BROKER.publishTelemetry(FLEET, "marker", reading("marker", "50", "2026-11-10T10:00:00Z"));

    awaitBattery("marker", 50);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vehicles", Integer.class)).isEqualTo(1);
  }

  @Test
  void messageThatBreaksTheContract_isDiscardedAndTheAdapterKeepsWorking() {
    vehicle("bike-0001");
    vehicle("marker");

    BROKER.publishTelemetry(
        FLEET, "bike-0001", reading("bike-0001", "50", "broken", "2026-11-10T10:00:00Z"));
    BROKER.publishTelemetry(FLEET, "bike-0001", "this is not json");
    BROKER.publishTelemetry(FLEET, "marker", reading("marker", "50", "2026-11-10T10:00:00Z"));

    awaitBattery("marker", 50);
    assertThat(row("bike-0001").get("battery_percent")).isNull();
  }

  // ------------------------------------------------------------- SLO2

  /** SLO2: updated within 5 s at 10 readings/s. 100 readings are sent at once, a harder case. */
  @Test
  void aBurstOfReadings_isAppliedWithinFiveSeconds() {
    List<String> codes = IntStream.rangeClosed(1, 100).mapToObj("bike-%04d"::formatted).toList();
    codes.forEach(this::vehicle);

    codes.forEach(
        code -> BROKER.publishTelemetry(FLEET, code, reading(code, "42", "2026-11-10T10:00:00Z")));

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() ->
            assertThat(
                    jdbc.queryForObject(
                        "SELECT COUNT(*) FROM vehicles WHERE battery_percent = 42", Integer.class))
                .isEqualTo(100));
  }
}
