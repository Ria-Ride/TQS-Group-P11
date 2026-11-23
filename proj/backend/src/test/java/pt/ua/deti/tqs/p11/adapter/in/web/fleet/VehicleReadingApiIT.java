package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * US09 (KAN-61): what the API shows of the last telemetry reading of a vehicle, so an operator
 * "always knows the real state of each vehicle". The readings are written to the database as the
 * telemetry adapter does (TelemetryIngestionIT covers the adapter).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class VehicleReadingApiIT {

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private FleetApi operator;
  private UUID hub03;

  @BeforeEach
  void loginAsOperatorWithOneHub() {
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
  }

  @Test
  void vehicleWithoutAReading_showsNothingOfIt() {
    UUID bike = operator.bike("bike-0001", hub03);

    ResponseEntity<Map<String, Object>> response = operator.get("/vehicles/" + bike);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("batteryPercent", null)
        .containsEntry("lockState", null)
        .containsEntry("latitude", null)
        .containsEntry("longitude", null)
        .containsEntry("lastReadingAt", null);
  }

  @Test
  void vehicleWithAReading_showsBatteryLockStatePositionAndTime() {
    UUID bike = operator.bike("bike-0001", hub03);
    jdbc.update(
        "UPDATE vehicles SET battery_percent = 78, lock_state = 'UNLOCKED', latitude = 40.6405, "
            + "longitude = -8.6538, last_reading_at = '2026-11-10T10:00:00Z' WHERE id = ?",
        bike);

    Map<String, Object> body = operator.get("/vehicles/" + bike).getBody();

    assertThat(body)
        .containsEntry("batteryPercent", 78)
        .containsEntry("lockState", "UNLOCKED")
        .containsEntry("latitude", 40.6405)
        .containsEntry("longitude", -8.6538)
        .containsEntry("lastReadingAt", "2026-11-10T10:00:00Z");
  }

  @Test
  void list_showsTheReadingOfEachVehicle() {
    UUID bike = operator.bike("bike-0001", hub03);
    jdbc.update("UPDATE vehicles SET battery_percent = 55 WHERE id = ?", bike);

    assertThat(operator.getList("/vehicles").getBody())
        .singleElement()
        .satisfies(vehicle -> assertThat(vehicle).containsEntry("batteryPercent", 55));
  }
}
