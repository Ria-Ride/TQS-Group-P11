package pt.ua.deti.tqs.p11.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;

/**
 * Steps for US13 (KAN-65), run against the real API and database. The database is emptied by the
 * hook in GuestAccountSteps; the admin is made in the database, as in production.
 */
public class AccountManagementSteps {

  private static final String PASSWORD = "Passw0rd!";

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private RestClient http;
  private String adminToken;
  private String userToken;
  private int lastStatus;

  private RestClient http() {
    if (http == null) {
      http = ApiClient.forPort(port);
    }
    return http;
  }

  /** A guest account given a role directly in the database; returns its token. */
  private String accountWithRole(String role, String email) {
    Registered account = ApiClient.registerGuest(http(), "Test " + role, email, PASSWORD);
    jdbc.update("UPDATE users SET role = ? WHERE id = ?", role, account.id());
    return account.token();
  }

  private UUID idOf(String email) {
    return jdbc.queryForObject(
        "SELECT id FROM users WHERE email = ?", UUID.class, new EmailAddress(email).value());
  }

  private int send(HttpMethod method, String path, String token, Object body) {
    RestClient.RequestBodySpec request =
        http()
            .method(method)
            .uri(path)
            .headers(
                headers -> {
                  if (token != null) {
                    headers.setBearerAuth(token);
                  }
                });
    if (body != null) {
      request.contentType(MediaType.APPLICATION_JSON).body(body);
    }
    return request.retrieve().toBodilessEntity().getStatusCode().value();
  }

  private int createAccount(
      String name, String email, String password, String role, boolean technician) {
    return send(
        HttpMethod.POST,
        "/admin/users",
        adminToken,
        Map.of(
            "name", name,
            "email", email,
            "password", password,
            "role", role,
            "fieldTechnician", technician));
  }

  @Given("an admin is logged in")
  public void anAdminIsLoggedIn() {
    adminToken = accountWithRole("ADMIN", "marta.reis@example.com");
  }

  @When(
      "the admin creates an account for {string} with email {string}, role {string} and field"
          + " technician {word}")
  public void theAdminCreatesAnAccount(String name, String email, String role, String technician) {
    lastStatus = createAccount(name, email, PASSWORD, role, Boolean.parseBoolean(technician));
  }

  @Given("the admin has created an operator account with email {string} and password {string}")
  public void theAdminHasCreatedAnOperator(String email, String password) {
    assertThat(createAccount("Rui Costa", email, password, "OPERATOR", false)).isEqualTo(201);
  }

  @Then("the account is created")
  public void theAccountIsCreated() {
    assertThat(lastStatus).isEqualTo(201);
  }

  @Then("the account with email {string} has role {string} and field technician {word}")
  public void theAccountHas(String email, String role, String technician) {
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT role, field_technician FROM users WHERE email = ?",
            new EmailAddress(email).value());
    assertThat(row)
        .containsEntry("role", role)
        .containsEntry("field_technician", Boolean.parseBoolean(technician));
  }

  @When("the admin changes the role of the account with email {string} to {string}")
  public void theAdminChangesTheRole(String email, String role) {
    lastStatus =
        send(
            HttpMethod.PATCH,
            "/admin/users/" + idOf(email) + "/role",
            adminToken,
            Map.of("role", role));
    assertThat(lastStatus).isEqualTo(200);
  }

  @When("the admin deactivates the account with email {string}")
  public void theAdminDeactivates(String email) {
    lastStatus =
        send(
            HttpMethod.PATCH,
            "/admin/users/" + idOf(email) + "/status",
            adminToken,
            Map.of("active", false));
    assertThat(lastStatus).isEqualTo(200);
  }

  @Then("logging in with email {string} and password {string} is refused")
  public void loggingInIsRefused(String email, String password) {
    assertThat(
            send(
                HttpMethod.POST,
                "/auth/login",
                null,
                Map.of("email", email, "password", password)))
        .isEqualTo(401);
  }

  @Given("a user with role {string} is logged in")
  public void aUserWithRoleIsLoggedIn(String role) {
    userToken =
        "ADMIN".equals(role)
            ? adminToken
            : accountWithRole(role, role.toLowerCase().replace('_', '.') + "@example.com");
  }

  @When("they request {word} {string}")
  public void theyRequest(String method, String path) {
    HttpMethod httpMethod = HttpMethod.valueOf(method);
    lastStatus =
        send(httpMethod, path, userToken, httpMethod == HttpMethod.GET ? null : Map.of());
  }

  @When("an anonymous visitor requests {word} {string}")
  public void anAnonymousVisitorRequests(String method, String path) {
    lastStatus = send(HttpMethod.valueOf(method), path, null, null);
  }

  @When("someone requests {word} {string} with an invalid token")
  public void someoneRequestsWithAnInvalidToken(String method, String path) {
    lastStatus = send(HttpMethod.valueOf(method), path, "not-a-jwt", null);
  }

  @Then("the response status is {int}")
  public void theResponseStatusIs(int status) {
    assertThat(lastStatus).isEqualTo(status);
  }
}