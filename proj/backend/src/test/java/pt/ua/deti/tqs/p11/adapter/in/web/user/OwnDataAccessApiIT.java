package pt.ua.deti.tqs.p11.adapter.in.web.user;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OwnDataAccessApiIT {

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private RestClient http;
  private Registered ana;
  private Registered bruno;

  @BeforeEach
  void registerTwoTourists() {
    jdbc.update("DELETE FROM users");
    http = ApiClient.forPort(port);
    ana = ApiClient.registerGuest(http, "Ana Sousa", "ana.sousa@example.com", "Passw0rd!");
    bruno = ApiClient.registerGuest(http, "Bruno Lima", "bruno.lima@example.com", "Secr3t!xy");
  }

  private ResponseEntity<Map<String, Object>> getUser(UUID id, String token) {
    return http.get()
        .uri("/users/{id}", id)
        .headers(headers -> {
          if (token != null) {
            headers.setBearerAuth(token);
          }
        })
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  @Test
  void ownData_returns200WithTheProfileButNoPasswordHash() {
    ResponseEntity<Map<String, Object>> response = getUser(ana.id(), ana.token());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("id", ana.id().toString())
        .containsEntry("name", "Ana Sousa")
        .containsEntry("email", "ana.sousa@example.com")
        .containsEntry("role", "END_USER")
        .containsEntry("fieldTechnician", false)
        .doesNotContainKey("passwordHash");
  }

  /** US11: the UI offers "My tickets" only to field technicians. */
  @Test
  void ownData_ofAFieldTechnician_saysSo() {
    jdbc.update(
        "UPDATE users SET role = 'OPERATOR', field_technician = TRUE WHERE id = ?", ana.id());

    ResponseEntity<Map<String, Object>> response = getUser(ana.id(), ana.token());

    assertThat(response.getBody())
        .containsEntry("role", "OPERATOR")
        .containsEntry("fieldTechnician", true);
  }

  @Test
  void anotherTouristsData_returns403() {
    assertThat(getUser(bruno.id(), ana.token()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void unknownIdWithAnEndUserToken_returns403NotRevealingWhetherItExists() {
    assertThat(getUser(UUID.randomUUID(), ana.token()).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }
}
