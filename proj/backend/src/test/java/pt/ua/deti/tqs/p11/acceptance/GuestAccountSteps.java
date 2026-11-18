package pt.ua.deti.tqs.p11.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.application.auth.AccessTokenService;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.support.ApiClient;

/** Steps for US01 (KAN-53), run against the real API and database. */
public class GuestAccountSteps {

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private AccessTokenService tokens;

  private RestClient http;
  private ResponseEntity<Map<String, Object>> lastResponse;

  /** Token of the tourist who logged in, used by the own-data steps. */
  private String accessToken;

  private ResponseEntity<Map<String, Object>> login(String email, String password) {
    return http.post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", email, "password", password))
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  @Before
  public void startFromAnEmptyDatabase() {
    jdbc.update("DELETE FROM users");
    http = ApiClient.forPort(port);
  }

  private ResponseEntity<Map<String, Object>> register(String name, String email, String password) {
    return http.post()
        .uri("/auth/guest")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("name", name, "email", email, "password", password))
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  private void registerSuccessfully(String name, String email, String password) {
    assertThat(register(name, email, password).getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Given("a guest account exists with name {string}, email {string} and password {string}")
  public void aGuestAccountExists(String name, String email, String password) {
    registerSuccessfully(name, email, password);
  }

  @Given("guest accounts exist:")
  public void guestAccountsExist(DataTable accounts) {
    accounts
        .asMaps()
        .forEach(
            row -> registerSuccessfully(row.get("name"), row.get("email"), row.get("password")));
  }

  @When("a tourist registers with name {string}, email {string} and password {string}")
  public void aTouristRegisters(String name, String email, String password) {
    lastResponse = register(name, email, password);
  }

  @Then("the registration is accepted")
  public void theRegistrationIsAccepted() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Then("the registration is refused as invalid")
  public void theRegistrationIsRefusedAsInvalid() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Then("the registration is refused because the email is already in use")
  public void theRegistrationIsRefusedBecauseTheEmailIsInUse() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Then("the response contains an access token")
  public void theResponseContainsAnAccessToken() {
    Map<String, Object> body = lastResponse.getBody();
    UUID userId = UUID.fromString((String) body.get("userId"));
    assertThat(tokens.verify((String) body.get("accessToken")))
        .hasValueSatisfying(claims -> assertThat(claims.userId()).isEqualTo(userId));
  }

  @Then("an account with email {string} exists with role {string}")
  public void anAccountExistsWithRole(String email, String role) {
    String storedRole =
        jdbc.queryForObject(
            "SELECT role FROM users WHERE email = ?",
            String.class,
            new EmailAddress(email).value());
    assertThat(storedRole).isEqualTo(role);
  }

  @Then("no account with email {string} exists")
  public void noAccountExists(String email) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM users WHERE email = ?",
            Integer.class,
            new EmailAddress(email).value());
    assertThat(count).isZero();
  }

  @When("a tourist logs in with email {string} and password {string}")
  public void aTouristLogsIn(String email, String password) {
    lastResponse = login(email, password);
  }

  @Given("the tourist is logged in with email {string} and password {string}")
  public void theTouristIsLoggedIn(String email, String password) {
    ResponseEntity<Map<String, Object>> response = login(email, password);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    accessToken = (String) response.getBody().get("accessToken");
  }

  @Then("the login is accepted")
  public void theLoginIsAccepted() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Then("the login is refused as unauthorized")
  public void theLoginIsRefusedAsUnauthorized() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @When("the tourist requests the data of the account with email {string}")
  public void theTouristRequestsTheDataOf(String email) {
    UUID id =
        jdbc.queryForObject(
            "SELECT id FROM users WHERE email = ?", UUID.class, new EmailAddress(email).value());
    lastResponse =
        http.get()
            .uri("/users/{id}", id)
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .toEntity(ApiClient.JSON_OBJECT);
  }

  @Then("access is granted")
  public void accessIsGranted() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Then("access is denied")
  public void accessIsDenied() {
    assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Then("the data shows name {string} and email {string}")
  public void theDataShows(String name, String email) {
    assertThat(lastResponse.getBody()).containsEntry("name", name).containsEntry("email", email);
  }
}
