package pt.ua.deti.tqs.p11.adapter.in.web.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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
import pt.ua.deti.tqs.p11.support.FleetApi;

/**
 * GET /vehicle-types over HTTP: the deposit each vehicle type needs (BR11), from the rules of use
 * in force, so the start page shows it before the tourist confirms (US04). Every logged-in role
 * reads it (access matrix).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class VehicleTypeApiIT {

  private static final String EMAIL = "ana.vehicletypes@example.com";

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private RestClient http;

  @AfterEach
  void removeTheRowsOfThisTest() {
    jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
  }

  @BeforeEach
  void client() {
    removeTheRowsOfThisTest();
    http = ApiClient.forPort(port);
  }

  @Test
  void vehicleTypes_eachWithItsDeposit() {
    String token = ApiClient.registerGuest(http, "Ana Tester", EMAIL, "Passw0rd!").token();

    ResponseEntity<List<Map<String, Object>>> response =
        http.get()
            .uri("/vehicle-types")
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .toEntity(FleetApi.JSON_LIST);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsExactly(
            Map.of("type", "E_BIKE", "deposit", "30.00", "currency", "EUR"),
            Map.of("type", "E_SCOOTER", "deposit", "50.00", "currency", "EUR"));
  }

  @Test
  void vehicleTypes_withoutAToken_is401() {
    assertThat(
            http.get().uri("/vehicle-types").retrieve().toBodilessEntity().getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}