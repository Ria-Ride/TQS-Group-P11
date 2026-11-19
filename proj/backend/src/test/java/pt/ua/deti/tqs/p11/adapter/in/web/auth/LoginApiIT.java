package pt.ua.deti.tqs.p11.adapter.in.web.auth;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.application.auth.AccessTokenService;
import pt.ua.deti.tqs.p11.support.ApiClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LoginApiIT {

  private static final String EMAIL = "ana.sousa@example.com";
  private static final String PASSWORD = "Passw0rd!";

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private AccessTokenService tokens;

  private RestClient http;
  private UUID anaId;

  @BeforeEach
  void registerAna() {
    jdbc.update("DELETE FROM users");
    http = ApiClient.forPort(port);
    anaId = ApiClient.registerGuest(http, "Ana Sousa", EMAIL, PASSWORD).id();
  }

  private ResponseEntity<Map<String, Object>> post(String uri, Map<String, String> body) {
    return http.post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  private ResponseEntity<Map<String, Object>> login(String email, String password) {
    return post("/auth/login", Map.of("email", email, "password", password));
  }

  @Test
  void validCredentials_return200AndATokenForThatUser() {
    ResponseEntity<Map<String, Object>> response = login(EMAIL, PASSWORD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(tokens.verify((String) response.getBody().get("accessToken")))
        .hasValueSatisfying(claims -> assertThat(claims.userId()).isEqualTo(anaId));
  }

  @Test
  void emailInAnotherCase_isAccepted() {
    assertThat(login("ANA.SOUSA@example.com", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void wrongPassword_returns401WithoutAToken() {
    ResponseEntity<Map<String, Object>> response = login(EMAIL, "WrongPass1!");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).doesNotContainKey("accessToken");
  }

  @Test
  void unknownEmail_returnsTheSame401AsAWrongPassword() {
    ResponseEntity<Map<String, Object>> unknown = login("bruno.lima@example.com", PASSWORD);
    ResponseEntity<Map<String, Object>> wrongPassword = login(EMAIL, "WrongPass1!");

    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unknown.getBody().get("detail")).isEqualTo(wrongPassword.getBody().get("detail"));
  }

  @Test
  void missingPassword_returns400() {
    ResponseEntity<Map<String, Object>> response = post("/auth/login", Map.of("email", EMAIL));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void passwordOver72Bytes_returns400() {
    assertThat(login(EMAIL, "ã".repeat(37)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
