package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.support.ApiClient;
import pt.ua.deti.tqs.p11.support.ApiClient.Registered;
import pt.ua.deti.tqs.p11.support.FleetApi;
import pt.ua.deti.tqs.p11.support.PaymentGatewayContainer;

/**
 * GET /pass-types, POST /passes and GET /passes (US03) over HTTP, with a real database and the
 * simulated gateway in a container. The rules are tested in the domain and use case tests; here the
 * status codes, the bodies, the error fields and the lock on a stay code.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PassApiIT {

  private static final PaymentGatewayContainer GATEWAY = new PaymentGatewayContainer();
  private static final UUID DAY_ID = UUID.fromString("7a1e0000-0000-4000-8000-000000000001");
  private static final UUID HOTEL_ID = UUID.fromString("5a7e0000-0000-4000-8000-000000000001");
  private static final String EMAILS = "'%.passapi@example.com'";

  @DynamicPropertySource
  static void gateway(DynamicPropertyRegistry registry) {
    GATEWAY.start();
    registry.add("app.payment.base-url", GATEWAY::baseUrl);
    registry.add("app.payment.read-timeout", () -> "PT1S");
  }

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private RestClient http;
  private Registered ana;
  private Registered rui;

  /**
   * Before and after each test: the context is shared with tests that delete every user, so no
   * pass, payment or stay may point to an account of this test when it ends.
   */
  @AfterEach
  void removeTheRowsOfThisTest() {
    String mine = "(SELECT id FROM users WHERE email LIKE " + EMAILS + ")";
    jdbc.update("DELETE FROM payments WHERE account_id IN " + mine);
    jdbc.update("DELETE FROM passes WHERE account_id IN " + mine);
    jdbc.update("DELETE FROM stays WHERE code LIKE 'API-%'");
    jdbc.update("DELETE FROM users WHERE email LIKE " + EMAILS);
  }

  @BeforeEach
  void twoTourists() {
    removeTheRowsOfThisTest();
    http = ApiClient.forPort(port);
    ana = ApiClient.registerGuest(http, "Ana Tester", "ana.passapi@example.com", "Passw0rd!");
    rui = ApiClient.registerGuest(http, "Rui Tester", "rui.passapi@example.com", "Passw0rd!");
  }

  private void stayRunningToday(String code) {
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Lisbon"));
    jdbc.update(
        "INSERT INTO stays (id, code, accommodation_id, check_in, check_out) VALUES (?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        code,
        HOTEL_ID,
        today.minusDays(1),
        today.plusDays(1));
  }

  private ResponseEntity<Map<String, Object>> buy(Registered who, Map<String, Object> body) {
    return http.post()
        .uri("/passes")
        .header("Authorization", "Bearer " + who.token())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  private ResponseEntity<Map<String, Object>> buy(Registered who, String stayCode, String card) {
    Map<String, Object> body = new HashMap<>();
    body.put("passTypeId", DAY_ID);
    body.put("paymentToken", card);
    if (stayCode != null) {
      body.put("stayCode", stayCode);
    }
    return buy(who, body);
  }

  private ResponseEntity<List<Map<String, Object>>> list(Registered who, String uri) {
    return http.get()
        .uri(uri)
        .header("Authorization", "Bearer " + who.token())
        .retrieve()
        .toEntity(FleetApi.JSON_LIST);
  }

  private static void assertFieldError(
      ResponseEntity<Map<String, Object>> response, HttpStatusCode status, String field) {
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getBody())
        .extractingByKey("errors")
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsKey(field);
  }

  /** The OpenAPI description is public and generated from the controllers, so it documents US03. */
  @Test
  void openApiDescription_isPublicAndDocumentsThePassEndpoints() {
    ResponseEntity<Map<String, Object>> response =
        http.get().uri("/v3/api-docs").retrieve().toEntity(ApiClient.JSON_OBJECT);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .extractingByKey("paths")
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsKeys("/passes", "/pass-types");
  }

  @Test
  void passTypes_listsTheSeededTypesWithPricesAsStrings() {
    ResponseEntity<List<Map<String, Object>>> response = list(ana, "/pass-types");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .extracting(type -> type.get("code"), type -> type.get("price"))
        .containsExactly(tuple("DAY", "10.00"), tuple("MULTI_DAY", "25.00"));
    assertThat(response.getBody().getFirst())
        .containsEntry("validityDays", 1)
        .containsEntry("currency", "EUR")
        .containsEntry("vehicleTypes", List.of("E_BIKE", "E_SCOOTER"));
  }

  private ResponseEntity<Map<String, Object>> price(Registered who, String query) {
    return http.get()
        .uri("/pass-types/" + DAY_ID + "/price" + query)
        .header("Authorization", "Bearer " + who.token())
        .retrieve()
        .toEntity(ApiClient.JSON_OBJECT);
  }

  @Test
  void price_withoutAStayCode_isTheBasePrice() {
    ResponseEntity<Map<String, Object>> response = price(ana, "");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("basePrice", "10.00")
        .containsEntry("price", "10.00")
        .containsEntry("discountPercent", 0)
        .containsEntry("currency", "EUR");
  }

  /** The discount is shown before paying, and the code stays free: nothing was bought yet. */
  @Test
  void price_withAValidStayCode_isDiscountedAndLinksNothing() {
    stayRunningToday("API-1002");

    ResponseEntity<Map<String, Object>> response = price(ana, "?stayCode=api-1002");

    assertThat(response.getBody())
        .containsEntry("price", "9.00")
        .containsEntry("discountPercent", 10);
    assertThat(
            jdbc.queryForObject(
                "SELECT redeemed_by FROM stays WHERE code = 'API-1002'", UUID.class))
        .isNull();
  }

  @Test
  void price_unknownStayCode_is400OnTheStayCodeField() {
    assertFieldError(price(ana, "?stayCode=API-NOPE"), HttpStatus.BAD_REQUEST, "stayCode");
  }

  @Test
  void buy_approved_is201WithThePassAndItIsListedForTheBuyerOnly() {
    ResponseEntity<Map<String, Object>> response = buy(ana, null, "tok_approve");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody())
        .containsEntry("passTypeCode", "DAY")
        .containsEntry("price", "10.00")
        .containsEntry("currency", "EUR")
        .containsKeys("id", "validFrom", "validTo");
    assertThat(list(ana, "/passes").getBody())
        .singleElement()
        .satisfies(pass -> assertThat(pass).containsEntry("id", response.getBody().get("id")));
    assertThat(list(rui, "/passes").getBody()).isEmpty();
  }

  @Test
  void buy_withAStayCode_is201AtTheDiscountedPrice() {
    stayRunningToday("API-1001");

    assertThat(buy(ana, "api-1001", "tok_approve").getBody()).containsEntry("price", "9.00");
  }

  @Test
  void buy_declined_is402OnThePaymentFieldAndNoPass() {
    assertFieldError(buy(ana, null, "tok_decline"), HttpStatus.PAYMENT_REQUIRED, "payment");
    assertThat(list(ana, "/passes").getBody()).isEmpty();
  }

  @Test
  void buy_timedOut_is504OnThePaymentFieldAndNoPass() {
    assertFieldError(buy(ana, null, "tok_timeout"), HttpStatus.GATEWAY_TIMEOUT, "payment");
    assertThat(list(ana, "/passes").getBody()).isEmpty();
  }

  @Test
  void buy_unknownStayCode_is400OnTheStayCodeField() {
    assertFieldError(buy(ana, "API-NOPE", "tok_approve"), HttpStatus.BAD_REQUEST, "stayCode");
  }

  @Test
  void buy_unknownPassType_is400OnThePassTypeField() {
    ResponseEntity<Map<String, Object>> response =
        buy(ana, Map.of("passTypeId", UUID.randomUUID(), "paymentToken", "tok_approve"));

    assertFieldError(response, HttpStatus.BAD_REQUEST, "passTypeId");
  }

  @Test
  void buy_missingPassTypeAndToken_is400OnBothFields() {
    ResponseEntity<Map<String, Object>> response = buy(ana, Map.of());

    assertFieldError(response, HttpStatus.BAD_REQUEST, "passTypeId");
    assertFieldError(response, HttpStatus.BAD_REQUEST, "paymentToken");
  }

  /**
   * BR9 under concurrency: two tourists send the same unlinked code at the same moment. The stay
   * row is locked while it is checked and linked, so exactly one gets it; the other is refused.
   * Without the lock this test fails; StayPersistenceAdapterIT also proves the lock without timing.
   */
  @Test
  void buy_sameCodeByTwoTouristsAtOnce_linksItToExactlyOne() throws Exception {
    stayRunningToday("API-RACE");
    CountDownLatch start = new CountDownLatch(1);
    Callable<HttpStatusCode> anaBuys =
        () -> {
          start.await();
          return buy(ana, "API-RACE", "tok_approve").getStatusCode();
        };
    Callable<HttpStatusCode> ruiBuys =
        () -> {
          start.await();
          return buy(rui, "API-RACE", "tok_approve").getStatusCode();
        };

    List<HttpStatusCode> statuses;
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<HttpStatusCode> first = pool.submit(anaBuys);
      Future<HttpStatusCode> second = pool.submit(ruiBuys);
      start.countDown();
      statuses = List.of(first.get(), second.get());
    }

    assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.BAD_REQUEST);
    Integer passes =
        jdbc.queryForObject(
            "SELECT count(*) FROM passes p JOIN stays s ON s.id = p.stay_id WHERE s.code = ?",
            Integer.class,
            "API-RACE");
    assertThat(passes).isEqualTo(1);
  }
}
