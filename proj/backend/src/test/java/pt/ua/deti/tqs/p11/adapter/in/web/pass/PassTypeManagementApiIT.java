package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import static org.assertj.core.api.Assertions.assertThat;

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
 * POST /pass-types and PATCH /pass-types/{id} (US14, BR8, BR23) over HTTP, as the client manager
 * and as other roles. The rules of a pass type are tested in PassTypeTest and
 * PassTypeManagementUseCaseTest; here the status codes, the bodies and the error fields. The pass
 * types of a test (code starting with IT_) are removed afterwards, so the seeded two stay the only
 * ones for the rest of the suite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PassTypeManagementApiIT {

  private static final String EMAILS = "'%.passtypeapi@example.com'";

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
        ApiClient.registerGuest(http, "Mara Silva", "mara.passtypeapi@example.com", "Passw0rd!");
    jdbc.update("UPDATE users SET role = 'CLIENT_MANAGER' WHERE id = ?", mara.id());
    manager = new FleetApi(http, mara.token());
    Registered rui =
        ApiClient.registerGuest(http, "Rui Costa", "rui.passtypeapi@example.com", "Passw0rd!");
    jdbc.update("UPDATE users SET role = 'OPERATOR' WHERE id = ?", rui.id());
    operator = new FleetApi(http, rui.token());
    Registered ana =
        ApiClient.registerGuest(http, "Ana Lopes", "ana.passtypeapi@example.com", "Passw0rd!");
    tourist = new FleetApi(http, ana.token());
  }

  @AfterEach
  void removeWhatThisTestMade() {
    jdbc.update(
        "DELETE FROM pass_type_vehicle_types WHERE pass_type_id IN"
            + " (SELECT id FROM pass_types WHERE code LIKE 'IT\\_%')");
    jdbc.update("DELETE FROM pass_types WHERE code LIKE 'IT\\_%'");
    jdbc.update("DELETE FROM users WHERE email LIKE " + EMAILS);
  }

  private static Map<String, Object> week() {
    Map<String, Object> body = new HashMap<>();
    body.put("code", "IT_WEEK");
    body.put("name", "Week pass");
    body.put("validityDays", 7);
    body.put("price", "40.00");
    body.put("vehicleTypes", List.of("E_BIKE"));
    return body;
  }

  private ResponseEntity<Map<String, Object>> create(FleetApi caller, Map<String, Object> body) {
    return caller.send(HttpMethod.POST, "/pass-types", body);
  }

  private ResponseEntity<Map<String, Object>> update(
      FleetApi caller, Object id, Map<String, Object> body) {
    return caller.send(HttpMethod.PATCH, "/pass-types/" + id, body);
  }

  private UUID createWeek() {
    return FleetApi.id(FleetApi.created(create(manager, week())));
  }

  // ------------------------------------------------------------- create

  @Test
  void post_validPassType_returns201WithTheSameShapeAsTheList() {
    ResponseEntity<Map<String, Object>> response = create(manager, week());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody())
        .containsKey("id")
        .containsEntry("code", "IT_WEEK")
        .containsEntry("name", "Week pass")
        .containsEntry("price", "40.00")
        .containsEntry("currency", "EUR")
        .containsEntry("validityDays", 7)
        .containsEntry("vehicleTypes", List.of("E_BIKE"));
  }

  @Test
  void post_thePassTypeAppearsInTheListEveryRoleReads() {
    createWeek();

    assertThat(tourist.getList("/pass-types").getBody())
        .extracting(type -> type.get("code"))
        .containsExactly("DAY", "MULTI_DAY", "IT_WEEK");
  }

  @Test
  void post_codeAlreadyInUse_returns409OnTheCode() {
    Map<String, Object> body = week();
    body.put("code", "DAY");

    ResponseEntity<Map<String, Object>> response = create(manager, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(FleetApi.errors(response)).containsKey("code");
  }

  @ParameterizedTest(name = "{0} = [{1}] is refused")
  @CsvSource(
      delimiter = '|',
      value = {
        "code | it_week",
        "code | 1WEEK",
        "code | ",
        "code | IT_WEEK_WITH_A_VERY_LONG_NAME_OVER_32",
        "name | ",
        "validityDays | 0",
        "validityDays | -1",
        "validityDays | 366",
        "price | -1",
        "price | 10.123",
        "price | abc",
        "price | "
      })
  void post_invalidField_returns400NamingIt(String field, String value) {
    Map<String, Object> body = week();
    if (field.equals("validityDays")) {
      body.put(field, Integer.valueOf(value));
    } else {
      body.put(field, value);
    }

    ResponseEntity<Map<String, Object>> response = create(manager, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(FleetApi.errors(response)).containsKey(field);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pass_types", Integer.class)).isEqualTo(2);
  }

  @Test
  void post_noVehicleTypeOrAnUnknownOne_returns400() {
    Map<String, Object> none = week();
    none.put("vehicleTypes", List.of());
    Map<String, Object> unknown = week();
    unknown.put("vehicleTypes", List.of("E_CAR"));

    assertThat(create(manager, none).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(FleetApi.errors(create(manager, none))).containsKey("vehicleTypes");
    assertThat(create(manager, unknown).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void post_aFreePassTypeAndTheLimits_areAccepted() {
    Map<String, Object> body = week();
    body.put("price", "0");
    body.put("validityDays", 365);
    body.put("vehicleTypes", List.of("E_BIKE", "E_SCOOTER"));

    ResponseEntity<Map<String, Object>> response = create(manager, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsEntry("price", "0.00");
  }

  // ------------------------------------------------------------- change

  @Test
  void patch_changesOnlyTheGivenFields() {
    UUID id = createWeek();

    ResponseEntity<Map<String, Object>> response = update(manager, id, Map.of("price", "45.50"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("id", id.toString())
        .containsEntry("code", "IT_WEEK")
        .containsEntry("name", "Week pass")
        .containsEntry("price", "45.50")
        .containsEntry("validityDays", 7)
        .containsEntry("vehicleTypes", List.of("E_BIKE"));
  }

  @Test
  void patch_everyField_isChanged_andTheCodeStaysFixed() {
    UUID id = createWeek();
    Map<String, Object> body = new HashMap<>();
    body.put("name", "Week pass plus");
    body.put("validityDays", 8);
    body.put("price", "50");
    body.put("vehicleTypes", List.of("E_BIKE", "E_SCOOTER"));
    body.put("code", "IT_OTHER");

    ResponseEntity<Map<String, Object>> response = update(manager, id, body);

    assertThat(response.getBody())
        .containsEntry("code", "IT_WEEK")
        .containsEntry("name", "Week pass plus")
        .containsEntry("validityDays", 8)
        .containsEntry("price", "50.00")
        .containsEntry("vehicleTypes", List.of("E_BIKE", "E_SCOOTER"));
  }

  @Test
  void patch_theNewPriceReachesThePriceQuoteOfTheNextPurchase() {
    UUID id = createWeek();
    update(manager, id, Map.of("price", "45.50"));

    ResponseEntity<Map<String, Object>> quote = tourist.get("/pass-types/" + id + "/price");

    assertThat(quote.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(quote.getBody()).containsEntry("price", "45.50");
  }

  @Test
  void patch_unknownPassType_returns404() {
    assertThat(update(manager, UUID.randomUUID(), Map.of("price", "5")).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void patch_invalidFields_return400AndChangeNothing() {
    UUID id = createWeek();

    ResponseEntity<Map<String, Object>> badDays = update(manager, id, Map.of("validityDays", 0));
    ResponseEntity<Map<String, Object>> badPrice = update(manager, id, Map.of("price", "-3"));
    ResponseEntity<Map<String, Object>> noTypes =
        update(manager, id, Map.of("vehicleTypes", List.of()));

    assertThat(FleetApi.errors(badDays)).containsKey("validityDays");
    assertThat(FleetApi.errors(badPrice)).containsKey("price");
    assertThat(FleetApi.errors(noTypes)).containsKey("vehicleTypes");
    assertThat(
            tourist.getList("/pass-types").getBody().stream()
                .filter(type -> "IT_WEEK".equals(type.get("code")))
                .findFirst()
                .orElseThrow())
        .containsEntry("price", "40.00")
        .containsEntry("validityDays", 7);
  }

  @Test
  void patch_emptyBody_changesNothing() {
    UUID id = createWeek();

    ResponseEntity<Map<String, Object>> response = update(manager, id, Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("price", "40.00");
  }

  // ------------------------------------------------------------- BR23: other roles

  @Test
  void otherRoles_cannotCreateOrChangePassTypes() {
    UUID id = createWeek();

    assertThat(create(tourist, week()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(create(operator, week()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(update(tourist, id, Map.of("price", "1")).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(update(operator, id, Map.of("price", "1")).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }
}
