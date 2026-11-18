package pt.ua.deti.tqs.p11.adapter.in.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.application.auth.AccessTokenService;
import pt.ua.deti.tqs.p11.application.auth.TokenClaims;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.support.ApiClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class GuestRegistrationApiIT {

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private AccessTokenService tokens;

  private RestClient http;

  @BeforeEach
  void setUp() {
    jdbc.update("DELETE FROM users");
    http = ApiClient.forPort(port);
  }

  private ResponseEntity<Map<String, Object>> register(Map<String, String> body) {
    return http.post()
        .uri("/auth/guest")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  private static Map<String, String> ana() {
    Map<String, String> body = new HashMap<>();
    body.put("name", "Ana Sousa");
    body.put("email", "ana.sousa@example.com");
    body.put("password", "Passw0rd!");
    return body;
  }

  private int accountsWithEmail(String email) {
    return jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email);
  }

  @Test
  void validGuest_returns201AndATokenForTheNewEndUser() {
    ResponseEntity<Map<String, Object>> response = register(ana());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UUID userId = UUID.fromString((String) response.getBody().get("userId"));
    TokenClaims claims = tokens.verify((String) response.getBody().get("accessToken")).orElseThrow();
    assertThat(claims.userId()).isEqualTo(userId);
    assertThat(claims.role()).isEqualTo(Role.END_USER);
  }

  @Test
  void validGuest_storesAHashNotThePassword() {
    register(ana());

    String stored =
        jdbc.queryForObject(
            "SELECT password_hash FROM users WHERE email = ?",
            String.class,
            "ana.sousa@example.com");
    assertThat(stored).isNotEqualTo("Passw0rd!").startsWith("$2");
  }

  @Test
  void missingName_returns400OnNameAndCreatesNoAccount() {
    Map<String, String> body = ana();
    body.remove("name");

    ResponseEntity<Map<String, Object>> response = register(body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat((Map<?, ?>) response.getBody().get("errors")).containsOnlyKeys("name");
    assertThat(accountsWithEmail("ana.sousa@example.com")).isZero();
  }

  @Test
  void emailWithoutDottedDomain_returns400OnEmail() {
    Map<String, String> body = ana();
    body.put("email", "ana.sousa@localhost");

    ResponseEntity<Map<String, Object>> response = register(body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat((Map<?, ?>) response.getBody().get("errors")).containsOnlyKeys("email");
  }

  @Test
  void emailAlreadyInUseWithDifferentCase_returns409AndKeepsOneAccount() {
    register(ana());
    Map<String, String> sameEmail = ana();
    sameEmail.put("email", "ANA.SOUSA@example.com");

    ResponseEntity<Map<String, Object>> response = register(sameEmail);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat((Map<?, ?>) response.getBody().get("errors")).containsOnlyKeys("email");
    assertThat(accountsWithEmail("ana.sousa@example.com")).isEqualTo(1);
  }
}