package pt.ua.deti.tqs.p11.adapter.in.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;

/**
 * US13 (KAN-75): 401 and 403 carry the same problem-details body as every other API error, and a
 * path that is not in the access matrix is denied by default.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SecurityErrorsApiIT {
  /** Ways a bearer token can be wrong; all must look the same to the client. */
  enum InvalidToken {
    MALFORMED,
    SIGNED_WITH_ANOTHER_KEY,
    EXPIRED
  }

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
    ana = ApiClient.registerGuest(http, "Ana Sousa", "ana.sousa@example.com", "Passw0rd!");
    bruno = ApiClient.registerGuest(http, "Bruno Lima", "bruno.lima@example.com", "Secr3t!xy");
  }

  private ResponseEntity<Map<String, Object>> get(String uri, String token) {
    return http.get()
        .uri(uri)
        .headers(headers -> {
          if (token != null) {
            headers.setBearerAuth(token);
          }
        })
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  private String token(InvalidToken kind) {
    return switch (kind) {
      case MALFORMED -> "not-a-jwt";
      case SIGNED_WITH_ANOTHER_KEY ->
          new JwtTokenService(
                  "other-test-secret-0123456789abcd", Duration.ofHours(1), Clock.systemUTC())
              .issue(ana.id(), Role.END_USER);
      case EXPIRED ->
          new JwtTokenService(
                  jwt.secret(),
                  Duration.ofMinutes(1),
                  Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC))
              .issue(ana.id(), Role.END_USER);
    };
  }

  /** Same shape as ApiExceptionHandler: RFC 9457 problem details plus an (empty) errors map. */
  private static void assertProblem(
      ResponseEntity<Map<String, Object>> response, HttpStatus status) {
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getHeaders().getContentType())
        .isNotNull()
        .satisfies(
            type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
    assertThat(response.getBody())
        .containsEntry("status", status.value())
        .containsKey("detail")
        .containsEntry("errors", Map.of());
  }

  @Test
  void noToken_returns401ProblemWithABearerChallenge() {
    ResponseEntity<Map<String, Object>> response = get("/users/" + ana.id(), null);

    assertProblem(response, HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
  }

  @ParameterizedTest
  @EnumSource(InvalidToken.class)
  void invalidToken_returnsThe401ProblemOfAMissingToken(InvalidToken kind) {
    ResponseEntity<Map<String, Object>> invalid = get("/users/" + ana.id(), token(kind));
    ResponseEntity<Map<String, Object>> missing = get("/users/" + ana.id(), null);

    assertProblem(invalid, HttpStatus.UNAUTHORIZED);
    // The client is not told why the token was refused.
    assertThat(invalid.getBody().get("detail")).isEqualTo(missing.getBody().get("detail"));
  }

  @Test
  void endUserOnAnAdminEndpoint_returns403Problem() {
    assertProblem(get("/admin/users", ana.token()), HttpStatus.FORBIDDEN);
  }

  @Test
  void ownershipDenial_returnsTheSame403Problem() {
    // This 403 comes from @PreAuthorize (method security), not from the URL rules.
    assertProblem(get("/users/" + bruno.id(), ana.token()), HttpStatus.FORBIDDEN);
  }

  @Test
  void pathNotInTheAccessMatrix_isDeniedByDefault() {
    assertProblem(get("/path-not-in-the-access-matrix", ana.token()), HttpStatus.FORBIDDEN);
  }
}
