package pt.ua.deti.tqs.p11.adapter.in.web.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import pt.ua.deti.tqs.p11.adapter.out.token.JwtTokenService;
import pt.ua.deti.tqs.p11.config.JwtProperties;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.support.ApiClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OwnDataAccessApiIT {

  private record Registered(UUID id, String token) {}

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private JwtProperties jwt;

  private RestClient http;
  private Registered ana;
  private Registered bruno;

  @BeforeEach
  void registerTwoTourists() {
    jdbc.update("DELETE FROM users");
    http = ApiClient.forPort(port);
    ana = register("Ana Sousa", "ana.sousa@example.com", "Passw0rd!");
    bruno = register("Bruno Lima", "bruno.lima@example.com", "Secr3t!xy");
  }

  private Registered register(String name, String email, String password) {
    Map<String, Object> body =
        http.post()
            .uri("/auth/guest")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("name", name, "email", email, "password", password))
            .retrieve()
            .toEntity(ApiClient.JSON_OBJECT)
            .getBody();
    return new Registered(
        UUID.fromString((String) body.get("userId")), (String) body.get("accessToken"));
  }

  private ResponseEntity<Map<String, Object>> getUser(UUID id, String token) {
    return http.get()
        .uri("/users/{id}", id)
        .headers(
            headers -> {
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
        .doesNotContainKey("passwordHash");
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

  @Test
  void noToken_returns401() {
    assertThat(getUser(ana.id(), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tokenSignedWithAnotherKey_returns401() {
    String foreign =
        new JwtTokenService("other-test-secret-0123456789abcd", Duration.ofHours(1), Clock.systemUTC())
            .issue(ana.id(), Role.END_USER);

    assertThat(getUser(ana.id(), foreign).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void expiredToken_returns401() {
    Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
    String expired =
        new JwtTokenService(jwt.secret(), Duration.ofMinutes(1), twoHoursAgo)
            .issue(ana.id(), Role.END_USER);

    assertThat(getUser(ana.id(), expired).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}