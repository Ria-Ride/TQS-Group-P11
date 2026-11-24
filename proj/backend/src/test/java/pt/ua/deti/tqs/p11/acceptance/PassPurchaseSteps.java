package pt.ua.deti.tqs.p11.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.MapAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;
import pt.ua.deti.tqs.p11.support.FleetApi;

/**
 * Steps for US03 (KAN-55), run against the real API, database and simulated payment gateway. The
 * tourist's test card decides the gateway's answer (see PaymentGatewayContainer). Stay codes are
 * written to the database, because the endpoint that creates them belongs to US14.
 */
public class PassPurchaseSteps {

  /** The hotel's calendar, as configured in app.passes.zone. */
  private static final ZoneId HOTEL_ZONE = ZoneId.of("Europe/Lisbon");

  private static final Map<String, String> CARDS =
      Map.of("approved", "tok_approve", "declined", "tok_decline", "timed out", "tok_timeout");

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private RestClient http;
  private final Map<String, Registered> tourists = new HashMap<>();
  private ResponseEntity<Map<String, Object>> lastPurchase;
  private ResponseEntity<List<Map<String, Object>>> passTypes;
  private Instant purchaseStartedAt;

  /** Runs before the hooks that delete users, because passes and stays point to them. */
  @Before(order = 9000)
  public void startWithNoPassesAndNoStays() {
    // Children first: payments point to passes, passes to stays.
    jdbc.update("DELETE FROM payments");
    jdbc.update("DELETE FROM passes");
    jdbc.update("DELETE FROM stays");
    http = ApiClient.forPort(port);
  }

  /** "today", "yesterday", "tomorrow", "2 days ago", "in 3 days", at the hotel. */
  @ParameterType("today|yesterday|tomorrow|\\d+ days ago|in \\d+ days")
  public LocalDate day(String text) {
    LocalDate today = LocalDate.now(HOTEL_ZONE);
    return switch (text) {
      case "today" -> today;
      case "yesterday" -> today.minusDays(1);
      case "tomorrow" -> today.plusDays(1);
      default ->
          text.startsWith("in ")
              ? today.plusDays(Long.parseLong(text.split(" ")[1]))
              : today.minusDays(Long.parseLong(text.split(" ")[0]));
    };
  }

  /** How the gateway answers the tourist's test card. */
  @ParameterType("approved|declined|timed out")
  public String outcome(String text) {
    return text;
  }

  // ------------------------------------------------------------- helpers

  private Registered tourist(String name) {
    return tourists.get(name);
  }

  private UUID passTypeId(String code) {
    return jdbc.queryForObject("SELECT id FROM pass_types WHERE code = ?", UUID.class, code);
  }

  private ResponseEntity<Map<String, Object>> buy(
      String name, String type, String stayCode, String outcome) {
    Map<String, Object> body = new HashMap<>();
    body.put("passTypeId", passTypeId(type));
    body.put("paymentToken", CARDS.get(outcome));
    if (stayCode != null) {
      body.put("stayCode", stayCode);
    }
    purchaseStartedAt = Instant.now();
    lastPurchase =
        http.post()
            .uri("/passes")
            .header("Authorization", "Bearer " + tourist(name).token())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toEntity(ApiClient.JSON_OBJECT);
    return lastPurchase;
  }

  /** The "errors" map of the problem body: field name to message. */
  private MapAssert<Object, Object> errors() {
    return assertThat(lastPurchase.getBody())
        .extractingByKey("errors")
        .asInstanceOf(InstanceOfAssertFactories.MAP);
  }

  // ------------------------------------------------------------- setup

  @Given("tourist {string} is logged in")
  public void touristIsLoggedIn(String name) {
    String email = name.toLowerCase() + "@example.com";
    tourists.put(name, ApiClient.registerGuest(http, name + " Tester", email, "Passw0rd!"));
  }

  /** The hotel and its discount are seeded (KAN-98); this checks the seed the scenarios rely on. */
  @Given("the partner hotel {string} gives {int}% off")
  public void thePartnerHotelGivesOff(String hotel, int percent) {
    Integer discount =
        jdbc.queryForObject(
            "SELECT discount_percent FROM partner_accommodations WHERE name = ?",
            Integer.class,
            hotel);
    assertThat(discount).as("seeded discount of %s", hotel).isEqualTo(percent);
  }

  @Given("stay code {string} of {string} runs from {day} to {day}")
  public void stayCodeRuns(String code, String hotel, LocalDate checkIn, LocalDate checkOut) {
    jdbc.update(
        """
        INSERT INTO stays (id, code, accommodation_id, check_in, check_out)
        SELECT ?, ?, id, ?, ? FROM partner_accommodations WHERE name = ?
        """,
        UUID.randomUUID(),
        code,
        checkIn,
        checkOut,
        hotel);
  }

  @Given("{string} has bought a {string} pass with stay code {string}")
  public void hasBoughtAPassWithStayCode(String name, String type, String stayCode) {
    assertThat(buy(name, type, stayCode, "approved").getStatusCode())
        .as("setup: %s buys a %s pass", name, type)
        .isEqualTo(HttpStatus.CREATED);
  }

  @Given(
      "{string} tried to buy a {string} pass with stay code {string} but the payment was declined")
  public void triedToBuyButDeclined(String name, String type, String stayCode) {
    assertThat(buy(name, type, stayCode, "declined").getStatusCode())
        .as("setup: %s's payment is declined", name)
        .isEqualTo(HttpStatus.PAYMENT_REQUIRED);
  }

  // ------------------------------------------------------------- actions

  @When("{string} looks at the pass types")
  public void looksAtThePassTypes(String name) {
    passTypes =
        http.get()
            .uri("/pass-types")
            .header("Authorization", "Bearer " + tourist(name).token())
            .retrieve()
            .toEntity(FleetApi.JSON_LIST);
  }

  @When("{string} buys a {string} pass")
  public void buysAPass(String name, String type) {
    buy(name, type, null, "approved");
  }

  @When("{string} buys a {string} pass and the payment is {outcome}")
  public void buysAPassAndThePaymentIs(String name, String type, String outcome) {
    buy(name, type, null, outcome);
  }

  @When("{string} buys a {string} pass with stay code {string}")
  public void buysAPassWithStayCode(String name, String type, String stayCode) {
    buy(name, type, stayCode, "approved");
  }

  // ------------------------------------------------------------- outcome

  @Then("{string} sees these pass types:")
  public void seesThesePassTypes(String name, DataTable table) {
    assertThat(passTypes.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<Map<String, Object>> shown = passTypes.getBody();
    assertThat(shown).hasSize(table.asMaps().size());
    for (Map<String, String> row : table.asMaps()) {
      assertThat(shown)
          .anySatisfy(type ->
              assertThat(type)
                  .containsEntry("code", row.get("code"))
                  .containsEntry("price", row.get("price"))
                  .containsEntry("currency", "EUR")
                  .containsEntry("validityDays", Integer.valueOf(row.get("validityDays"))));
    }
  }

  @Then("{string} gets a {string} pass for {bigdecimal} EUR")
  public void getsAPassFor(String name, String type, BigDecimal price) {
    assertThat(lastPurchase.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Map<String, Object> pass = lastPurchase.getBody();
    assertThat(pass).containsEntry("passTypeCode", type).containsEntry("currency", "EUR");
    // Compared as strings: "9.00" and "9.0" are different answers to the tourist.
    assertThat(pass.get("price")).isEqualTo(price.setScale(2).toPlainString());
    Integer stored =
        jdbc.queryForObject(
            "SELECT count(*) FROM passes WHERE account_id = ? AND id = ?",
            Integer.class,
            tourist(name).id(),
            UUID.fromString((String) pass.get("id")));
    assertThat(stored).as("the pass is stored for %s", name).isEqualTo(1);
  }

  @Then("the pass is valid for {int} hours from the moment of purchase")
  public void thePassIsValidForHours(int hours) {
    Instant validFrom = Instant.parse((String) lastPurchase.getBody().get("validFrom"));
    Instant validTo = Instant.parse((String) lastPurchase.getBody().get("validTo"));
    assertThat(validFrom).isBetween(purchaseStartedAt.minusSeconds(1), Instant.now());
    assertThat(Duration.between(validFrom, validTo)).isEqualTo(Duration.ofHours(hours));
  }

  @Then("the purchase is refused because the payment was declined")
  public void refusedBecauseDeclined() {
    assertThat(lastPurchase.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
    errors().containsKey("payment");
  }

  @Then("the purchase is refused because the payment was timed out")
  public void refusedBecauseTimedOut() {
    assertThat(lastPurchase.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    errors().containsKey("payment");
  }

  @Then("{string} has no pass")
  public void hasNoPass(String name) {
    Integer passes =
        jdbc.queryForObject(
            "SELECT count(*) FROM passes WHERE account_id = ?", Integer.class, tourist(name).id());
    assertThat(passes).isZero();
  }

  @Then("the stay code is accepted")
  public void theStayCodeIsAccepted() {
    assertThat(lastPurchase.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(lastPurchase.getBody()).containsEntry("price", "9.00");
  }

  @Then("the stay code is refused as {}")
  public void theStayCodeIsRefusedAs(String reason) {
    assertThat(lastPurchase.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    String expected =
        switch (reason) {
          case "unknown" -> "is unknown";
          case "not valid today" -> "is not valid today";
          case "linked to another account" -> "is linked to another account";
          default -> throw new IllegalArgumentException("Unknown reason: " + reason);
        };
    errors().containsEntry("stayCode", expected);
  }
}
