package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;
import pt.ua.deti.tqs.p11.support.FleetApi;

/**
 * Partner hotels and stay codes over HTTP (US14, BR9, BR23), as the client manager and as other
 * roles. The code is made by the platform; the last test shows that a code issued here gives the
 * discount in the price a tourist is quoted (US03). The hotels of a test (name starting with IT
 * HOTEL) and their stays are removed afterwards, so the seeded hotel and stays stay the only ones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class StayCodeApiIT {

  private static final String EMAILS = "'%.stayapi@example.com'";
  private static final UUID DAY_ID = UUID.fromString("7a1e0000-0000-4000-8000-000000000001");
  private static final ZoneId HOTEL_ZONE = ZoneId.of("Europe/Lisbon");

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private FleetApi manager;
  private FleetApi operator;
  private FleetApi tourist;

  @BeforeEach
  void threeAccounts() {
    removeWhatThisTestMade();
    RestClient http = ApiClient.forPort(port);
    Registered mara =
        ApiClient.registerGuest(http, "Mara Silva", "mara.stayapi@example.com", "Passw0rd!");
    jdbc.update("UPDATE users SET role = 'CLIENT_MANAGER' WHERE id = ?", mara.id());
    manager = new FleetApi(http, mara.token());
    Registered rui =
        ApiClient.registerGuest(http, "Rui Costa", "rui.stayapi@example.com", "Passw0rd!");
    jdbc.update("UPDATE users SET role = 'OPERATOR' WHERE id = ?", rui.id());
    operator = new FleetApi(http, rui.token());
    Registered ana =
        ApiClient.registerGuest(http, "Ana Lopes", "ana.stayapi@example.com", "Passw0rd!");
    tourist = new FleetApi(http, ana.token());
  }

  @AfterEach
  void removeWhatThisTestMade() {
    jdbc.update(
        "DELETE FROM stays WHERE accommodation_id IN"
            + " (SELECT id FROM partner_accommodations WHERE name LIKE 'IT HOTEL%')");
    jdbc.update("DELETE FROM partner_accommodations WHERE name LIKE 'IT HOTEL%'");
    jdbc.update("DELETE FROM users WHERE email LIKE " + EMAILS);
  }

  private UUID hotel(String name, int discount) {
    return FleetApi.id(
        FleetApi.created(
            manager.send(
                HttpMethod.POST,
                "/partner-accommodations",
                Map.of("name", name, "discountPercent", discount))));
  }

  private ResponseEntity<Map<String, Object>> issue(
      FleetApi caller, Object hotel, Object checkIn, Object checkOut) {
    Map<String, Object> body = new HashMap<>();
    body.put("accommodationId", hotel);
    body.put("checkIn", checkIn);
    body.put("checkOut", checkOut);
    return caller.send(HttpMethod.POST, "/stay-codes", body);
  }

  // ------------------------------------------------------------- hotels

  @Test
  void postHotel_returns201WithTheDiscount() {
    ResponseEntity<Map<String, Object>> response =
        manager.send(
            HttpMethod.POST,
            "/partner-accommodations",
            Map.of("name", "IT HOTEL Laguna", "discountPercent", 15));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody())
        .containsKey("id")
        .containsEntry("name", "IT HOTEL Laguna")
        .containsEntry("discountPercent", 15);
  }

  @Test
  void getHotels_listsTheSeededHotelAndTheNewOnes_byName() {
    hotel("IT HOTEL Laguna", 15);

    assertThat(manager.getList("/partner-accommodations").getBody())
        .extracting(found -> found.get("name"))
        .containsSubsequence("Hotel Ria Azul", "IT HOTEL Laguna");
  }

  @Test
  void postHotel_nameInUse_returns409OnTheName() {
    hotel("IT HOTEL Laguna", 15);

    ResponseEntity<Map<String, Object>> response =
        manager.send(
            HttpMethod.POST,
            "/partner-accommodations",
            Map.of("name", "IT HOTEL Laguna", "discountPercent", 5));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(FleetApi.errors(response)).containsKey("name");
  }

  @ParameterizedTest(name = "name [{0}] discount {1} is refused")
  @CsvSource(
      delimiter = '|',
      value = {"IT HOTEL X | 101", "IT HOTEL X | -1", " | 10"})
  void postHotel_invalidFields_return400(String name, int discount) {
    ResponseEntity<Map<String, Object>> response =
        manager.send(
            HttpMethod.POST,
            "/partner-accommodations",
            Map.of("name", name == null ? "" : name, "discountPercent", discount));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(FleetApi.errors(response)).isNotEmpty();
  }

  @Test
  void postHotel_discountLimits_areAccepted() {
    for (int discount : new int[] {0, 100}) {
      assertThat(
              manager
                  .send(
                      HttpMethod.POST,
                      "/partner-accommodations",
                      Map.of("name", "IT HOTEL " + discount, "discountPercent", discount))
                  .getStatusCode())
          .isEqualTo(HttpStatus.CREATED);
    }
  }

  // ------------------------------------------------------------- stay codes

  @Test
  void postStayCode_returns201WithAGeneratedCodeForTheHotel() {
    UUID hotel = hotel("IT HOTEL Laguna", 15);

    ResponseEntity<Map<String, Object>> response =
        issue(manager, hotel, "2026-12-01", "2026-12-05");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody())
        .containsKey("id")
        .containsEntry("accommodationId", hotel.toString())
        .containsEntry("accommodationName", "IT HOTEL Laguna")
        .containsEntry("discountPercent", 15)
        .containsEntry("checkIn", "2026-12-01")
        .containsEntry("checkOut", "2026-12-05")
        .containsEntry("redeemed", false);
    assertThat((String) response.getBody().get("code")).matches("[A-Z0-9-]{6,32}");
  }

  @Test
  void postStayCode_twoCodes_areDifferent() {
    UUID hotel = hotel("IT HOTEL Laguna", 15);

    String first = (String) issue(manager, hotel, "2026-12-01", "2026-12-05").getBody().get("code");
    String second =
        (String) issue(manager, hotel, "2026-12-01", "2026-12-05").getBody().get("code");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void postStayCode_aOneDayStay_isAccepted() {
    UUID hotel = hotel("IT HOTEL Laguna", 15);

    assertThat(issue(manager, hotel, "2026-12-01", "2026-12-01").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void postStayCode_checkOutBeforeCheckIn_returns400OnCheckOut() {
    UUID hotel = hotel("IT HOTEL Laguna", 15);

    ResponseEntity<Map<String, Object>> response =
        issue(manager, hotel, "2026-12-05", "2026-12-01");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(FleetApi.errors(response)).containsKey("checkOut");
  }

  @Test
  void postStayCode_unknownHotelMissingOrBadFields_return400() {
    UUID hotel = hotel("IT HOTEL Laguna", 15);

    ResponseEntity<Map<String, Object>> unknown =
        issue(manager, UUID.randomUUID(), "2026-12-01", "2026-12-05");
    ResponseEntity<Map<String, Object>> missing = issue(manager, hotel, null, null);
    ResponseEntity<Map<String, Object>> badDate = issue(manager, hotel, "01/12/2026", "2026-12-05");

    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(FleetApi.errors(unknown)).containsKey("accommodationId");
    assertThat(FleetApi.errors(missing)).containsKeys("checkIn", "checkOut");
    assertThat(badDate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void getStayCodes_listsTheIssuedOnesWithTheirHotelAndDates_newestCheckInFirst() {
    UUID hotel = hotel("IT HOTEL Laguna", 15);
    String code = (String) issue(manager, hotel, "2030-06-01", "2030-06-03").getBody().get("code");

    List<Map<String, Object>> codes = manager.getList("/stay-codes").getBody();

    assertThat(codes.getFirst())
        .containsEntry("code", code)
        .containsEntry("accommodationName", "IT HOTEL Laguna")
        .containsEntry("discountPercent", 15)
        .containsEntry("checkIn", "2030-06-01")
        .containsEntry("checkOut", "2030-06-03")
        .containsEntry("redeemed", false);
    assertThat(codes).extracting(found -> found.get("code")).contains("RIA-DEMO-01");
  }

  // ------------------------------------------------------------- BR23

  @Test
  void otherRoles_cannotReadOrIssueAnything() {
    UUID hotel = hotel("IT HOTEL Laguna", 15);

    assertThat(issue(tourist, hotel, "2026-12-01", "2026-12-05").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(issue(operator, hotel, "2026-12-01", "2026-12-05").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(tourist.get("/stay-codes").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(operator.get("/partner-accommodations").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            tourist
                .send(
                    HttpMethod.POST,
                    "/partner-accommodations",
                    Map.of("name", "IT HOTEL Spy", "discountPercent", 50))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  // ------------------------------------------------------------- the discount reaches the tourist

  /** BR9: a code issued here lowers the quoted price by the discount of its hotel. */
  @Test
  void aCodeIssuedHere_givesTheDiscountOfItsHotel_inThePriceQuote() {
    UUID hotel = hotel("IT HOTEL Laguna", 20);
    LocalDate today = LocalDate.now(HOTEL_ZONE);
    String code =
        (String)
            issue(manager, hotel, today.minusDays(1).toString(), today.plusDays(1).toString())
                .getBody()
                .get("code");

    ResponseEntity<Map<String, Object>> quote =
        tourist.get("/pass-types/" + DAY_ID + "/price?stayCode=" + code);

    assertThat(quote.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(quote.getBody()).containsEntry("price", "8.00");
  }
}
