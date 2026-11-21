package pt.ua.deti.tqs.p11.adapter.in.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;

/**
 * US13 (KAN-77): on every request the account is read from the database, so deactivating it,
 * deleting it or changing its role takes effect at once, even for tokens issued before.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AccountStatusApiIT {

  private static final String EMAIL = "ana.sousa@example.com";
  private static final String PASSWORD = "Passw0rd!";

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private RestClient http;
  private Registered ana;

  @BeforeEach
  void registerAna() {
    jdbc.update("DELETE FROM users");
    http = ApiClient.forPort(port);
    ana = ApiClient.registerGuest(http, "Ana Sousa", EMAIL, PASSWORD);
  }

  private HttpStatusCode get(String uri, String token) {
    return http.get()
        .uri(uri)
        .headers(headers -> headers.setBearerAuth(token))
        .retrieve()
        .toBodilessEntity()
        .getStatusCode();
  }

  private String login() {
    Map<String, Object> body =
        http.post()
            .uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", EMAIL, "password", PASSWORD))
            .retrieve()
            .toEntity(ApiClient.JSON_OBJECT)
            .getBody();
    return (String) body.get("accessToken");
  }

  @Test
  void tokenOfAnAccountDeactivatedAfterLogin_returns401() {
    jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", ana.id());

    assertThat(get("/users/" + ana.id(), ana.token())).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tokenOfADeletedAccount_returns401() {
    jdbc.update("DELETE FROM users WHERE id = ?", ana.id());

    assertThat(get("/users/" + ana.id(), ana.token())).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  /** Admin roles are changed directly in the database; an old ADMIN token must lose its power. */
  @Test
  void adminTokenIssuedBeforeADemotion_isForbiddenOnAdminEndpoints() {
    jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", ana.id());
    String adminToken = login();
    jdbc.update("UPDATE users SET role = 'END_USER' WHERE id = ?", ana.id());

    assertThat(get("/admin/users", adminToken)).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
