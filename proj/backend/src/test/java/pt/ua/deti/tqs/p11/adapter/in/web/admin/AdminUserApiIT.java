package pt.ua.deti.tqs.p11.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;

/** US13 (KAN-77): admin account management through /admin/users. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AdminUserApiIT {

  private static final String PASSWORD = "Passw0rd!";
  private static final String ADMIN_EMAIL = "marta.reis@example.com";
  private static final String RUI_EMAIL = "rui.costa@example.com";

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private RestClient http;
  private UUID adminId;
  private String adminToken;

  /**
   * Admins are made in the database. The guest token works as an admin token right away, because
   * the role is read from the database on every request.
   */
  @BeforeEach
  void createAdmin() {
    jdbc.update("DELETE FROM users");
    http = ApiClient.forPort(port);
    Registered marta = ApiClient.registerGuest(http, "Marta Reis", ADMIN_EMAIL, PASSWORD);
    jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", marta.id());
    adminId = marta.id();
    adminToken = marta.token();
  }

  private ResponseEntity<Map<String, Object>> send(HttpMethod method, String uri, Object body) {
    return http.method(method)
        .uri(uri)
        .headers(headers -> headers.setBearerAuth(adminToken))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  private ResponseEntity<Map<String, Object>> createRui(String role, boolean fieldTechnician) {
    return send(
        HttpMethod.POST,
        "/admin/users",
        Map.of(
            "name", "Rui Costa",
            "email", RUI_EMAIL,
            "password", PASSWORD,
            "role", role,
            "fieldTechnician", fieldTechnician));
  }

  /** Setup for the tests that change an existing account. */
  private UUID createOperatorRui() {
    ResponseEntity<Map<String, Object>> response = createRui("OPERATOR", false);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return UUID.fromString((String) response.getBody().get("id"));
  }

  private HttpStatusCode loginRui() {
    return http.post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", RUI_EMAIL, "password", PASSWORD))
        .retrieve()
        .toBodilessEntity()
        .getStatusCode();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> errors(ResponseEntity<Map<String, Object>> response) {
    return (Map<String, Object>) response.getBody().get("errors");
  }

  // Create

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void create_operatorWithOrWithoutFieldTechnician_returns201AndTheAccountCanLogIn(
      boolean fieldTechnician) {
    ResponseEntity<Map<String, Object>> response = createRui("OPERATOR", fieldTechnician);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody())
        .containsEntry("name", "Rui Costa")
        .containsEntry("email", RUI_EMAIL)
        .containsEntry("role", "OPERATOR")
        .containsEntry("fieldTechnician", fieldTechnician)
        .containsEntry("active", true)
        .doesNotContainKeys("password", "passwordHash");
    assertThat(loginRui()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void create_admin_returns409AndCreatesNothing() {
    assertThat(createRui("ADMIN", false).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(loginRui()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  /** BR20: only an OPERATOR can be a field technician. */
  @Test
  void create_clientManagerAsFieldTechnician_returns400OnThatField() {
    ResponseEntity<Map<String, Object>> response = createRui("CLIENT_MANAGER", true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsKey("fieldTechnician");
  }

  @Test
  void create_invalidFields_returns400NamingEachOne() {
    ResponseEntity<Map<String, Object>> response =
        send(
            HttpMethod.POST,
            "/admin/users",
            Map.of("name", "", "email", "not-an-email", "password", "short"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsOnlyKeys("name", "email", "password", "role");
  }

  @Test
  void create_emailAlreadyInUse_returns409() {
    ResponseEntity<Map<String, Object>> response =
        send(
            HttpMethod.POST,
            "/admin/users",
            Map.of(
                "name",
                "Marta R.",
                "email",
                ADMIN_EMAIL,
                "password",
                PASSWORD,
                "role",
                "OPERATOR"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  // List

  @Test
  @SuppressWarnings("unchecked")
  void list_returnsEveryAccountOrderedByNameWithoutPasswords() {
    createOperatorRui();

    ResponseEntity<Object> response =
        http.get()
            .uri("/admin/users")
            .headers(headers -> headers.setBearerAuth(adminToken))
            .retrieve()
            .toEntity(Object.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isInstanceOf(List.class);
    List<Map<String, Object>> accounts = (List<Map<String, Object>>) response.getBody();
    assertThat(accounts)
        .extracting(account -> account.get("name"))
        .containsExactly("Marta Reis", "Rui Costa");
    assertThat(accounts)
        .allSatisfy(account -> assertThat(account).doesNotContainKey("passwordHash"));
  }

  // Change role

  @Test
  void changeRole_operatorToClientManager_returns200WithTheNewRole() {
    UUID rui = createOperatorRui();

    ResponseEntity<Map<String, Object>> response =
        send(HttpMethod.PATCH, "/admin/users/" + rui + "/role", Map.of("role", "CLIENT_MANAGER"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("role", "CLIENT_MANAGER")
        .containsEntry("fieldTechnician", false);
  }

  @Test
  void changeRole_ofAnAdmin_returns409() {
    ResponseEntity<Map<String, Object>> response =
        send(HttpMethod.PATCH, "/admin/users/" + adminId + "/role", Map.of("role", "OPERATOR"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void changeRole_unknownAccount_returns404() {
    ResponseEntity<Map<String, Object>> response =
        send(
            HttpMethod.PATCH,
            "/admin/users/" + UUID.randomUUID() + "/role",
            Map.of("role", "OPERATOR"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    // Our problem body, not the 404 of a path that has no endpoint.
    assertThat(response.getBody()).containsKey("errors");
  }

  // Status

  @Test
  void deactivate_operator_returns200AndBlocksTheirLogin() {
    UUID rui = createOperatorRui();

    ResponseEntity<Map<String, Object>> response =
        send(HttpMethod.PATCH, "/admin/users/" + rui + "/status", Map.of("active", false));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("active", false);
    assertThat(loginRui()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void reactivate_operator_letsThemLogInAgain() {
    UUID rui = createOperatorRui();
    send(HttpMethod.PATCH, "/admin/users/" + rui + "/status", Map.of("active", false));

    send(HttpMethod.PATCH, "/admin/users/" + rui + "/status", Map.of("active", true));

    assertThat(loginRui()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void deactivate_themselves_returns409() {
    ResponseEntity<Map<String, Object>> response =
        send(HttpMethod.PATCH, "/admin/users/" + adminId + "/status", Map.of("active", false));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void status_withoutActive_returns400() {
    ResponseEntity<Map<String, Object>> response =
        send(HttpMethod.PATCH, "/admin/users/" + adminId + "/status", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response)).containsKey("active");
  }
}
