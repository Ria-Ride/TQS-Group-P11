package pt.ua.deti.tqs.p11.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** The deposit endpoints of the gateway, through a real server on a random port. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "gateway.timeout-delay=PT0.3S")
class AuthorizationApiIT {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

  @Value("${local.server.port}")
  private int port;

  private RestClient http;

  @BeforeEach
  void client() {
    http =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
  }

  private ResponseEntity<Map<String, Object>> post(String uri, Object body) {
    return http.post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toEntity(JSON);
  }

  private ResponseEntity<Map<String, Object>> authorize(String key, String amount, String token) {
    return post(
        "/authorizations",
        Map.of("idempotencyKey", key, "amount", amount, "currency", "EUR", "token", token));
  }

  /** A new key with 30.00 EUR held on the approving card. */
  private String authorized() {
    String key = UUID.randomUUID().toString();
    assertThat(authorize(key, "30.00", "tok_approve").getStatusCode())
        .as("setup: authorize")
        .isEqualTo(HttpStatus.CREATED);
    return key;
  }

  private ResponseEntity<Map<String, Object>> capture(String key, String amount) {
    return post("/authorizations/" + key + "/capture", Map.of("amount", amount));
  }

  private ResponseEntity<Map<String, Object>> release(String key) {
    return post("/authorizations/" + key + "/release", Map.of());
  }

  // ------------------------------------------------------------- authorize

  @Test
  void authorize_approvingCard_is201Authorized() {
    ResponseEntity<Map<String, Object>> response =
        authorize(UUID.randomUUID().toString(), "30.00", "tok_approve");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody())
        .containsEntry("status", "AUTHORIZED")
        .containsEntry("amount", "30.00")
        .containsEntry("currency", "EUR")
        .containsKey("id");
  }

  @Test
  void authorize_decliningCard_is402Declined() {
    ResponseEntity<Map<String, Object>> response =
        authorize(UUID.randomUUID().toString(), "30.00", "tok_decline");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
    assertThat(response.getBody())
        .containsEntry("status", "DECLINED")
        .containsEntry("reason", "card_declined");
  }

  @Test
  void authorize_timeoutCard_holdsTheAmountButAnswersOnlyAfterTheDelay() {
    Instant start = Instant.now();

    ResponseEntity<Map<String, Object>> response =
        authorize(UUID.randomUUID().toString(), "30.00", "tok_timeout");

    assertThat(Duration.between(start, Instant.now()))
        .isGreaterThanOrEqualTo(Duration.ofMillis(300));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsEntry("status", "AUTHORIZED");
  }

  @Test
  void authorize_sameKey_returnsTheSameAuthorization() {
    String key = UUID.randomUUID().toString();
    Object firstId = authorize(key, "30.00", "tok_approve").getBody().get("id");

    ResponseEntity<Map<String, Object>> again = authorize(key, "30.00", "tok_approve");

    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(again.getBody()).containsEntry("id", firstId);
  }

  @Test
  void authorize_sameKeyWithAnotherAmount_is409() {
    String key = authorized();

    assertThat(authorize(key, "50.00", "tok_approve").getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void authorize_negativeAmount_is400() {
    assertThat(authorize("k", "-1.00", "tok_approve").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // ------------------------------------------------------------- capture

  @Test
  void capture_partOfTheAmount_is200WithTheCapturedAndReleasedAmounts() {
    String key = authorized();

    ResponseEntity<Map<String, Object>> response = capture(key, "4.00");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("status", "CAPTURED")
        .containsEntry("captured", "4.00")
        .containsEntry("released", "26.00");
  }

  @Test
  void capture_moreThanWasHeld_is400() {
    String key = authorized();

    assertThat(capture(key, "30.01").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void capture_amountWithThreeDecimals_is400() {
    String key = authorized();

    assertThat(capture(key, "1.001").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void capture_afterARelease_is409() {
    String key = authorized();
    release(key);

    assertThat(capture(key, "4.00").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void capture_unknownKey_is404() {
    assertThat(capture("nope", "4.00").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ------------------------------------------------------------- release

  @Test
  void release_authorized_is200Released() {
    String key = authorized();

    ResponseEntity<Map<String, Object>> response = release(key);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("status", "RELEASED")
        .containsEntry("released", "30.00");
  }

  @Test
  void release_afterATimeout_givesBackTheHeldAmount() {
    String key = UUID.randomUUID().toString();
    authorize(key, "30.00", "tok_timeout");

    assertThat(release(key).getBody()).containsEntry("status", "RELEASED");
  }

  @Test
  void release_afterACapture_is409() {
    String key = authorized();
    capture(key, "4.00");

    assertThat(release(key).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void release_unknownKey_is404() {
    assertThat(release("nope").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}