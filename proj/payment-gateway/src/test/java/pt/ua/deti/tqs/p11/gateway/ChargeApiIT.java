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

/** The gateway's HTTP API, through a real server on a random port. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "gateway.timeout-delay=PT0.3S")
class ChargeApiIT {

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

  private ResponseEntity<Map<String, Object>> charge(Map<String, Object> body) {
    return http.post()
        .uri("/charges")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toEntity(JSON);
  }

  private ResponseEntity<Map<String, Object>> charge(
      String key, String amount, String currency, String token) {
    return charge(
        Map.of("idempotencyKey", key, "amount", amount, "currency", currency, "token", token));
  }

  private ResponseEntity<Map<String, Object>> charge(String key, String token) {
    return charge(key, "9.00", "EUR", token);
  }

  /** A charge of 9.00 EUR with a new idempotency key. */
  private ResponseEntity<Map<String, Object>> charge(String token) {
    return charge(UUID.randomUUID().toString(), token);
  }

  @Test
  void approvingCard_is201Approved() {
    ResponseEntity<Map<String, Object>> response = charge("tok_approve");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody())
        .containsEntry("status", "APPROVED")
        .containsEntry("amount", "9.00")
        .containsEntry("currency", "EUR")
        .containsKey("id");
  }

  @Test
  void decliningCard_is402Declined() {
    ResponseEntity<Map<String, Object>> response = charge("tok_decline");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
    assertThat(response.getBody())
        .containsEntry("status", "DECLINED")
        .containsEntry("reason", "card_declined");
  }

  @Test
  void timeoutCard_answersOnlyAfterTheDelay() {
    Instant start = Instant.now();

    ResponseEntity<Map<String, Object>> response = charge("tok_timeout");

    assertThat(Duration.between(start, Instant.now()))
        .isGreaterThanOrEqualTo(Duration.ofMillis(300));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
    assertThat(response.getBody()).containsEntry("reason", "timeout");
  }

  @Test
  void sameIdempotencyKey_returnsTheSameCharge() {
    String key = UUID.randomUUID().toString();
    Object firstId = charge(key, "tok_approve").getBody().get("id");

    ResponseEntity<Map<String, Object>> again = charge(key, "tok_approve");

    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(again.getBody()).containsEntry("id", firstId);
  }

  @Test
  void sameIdempotencyKeyWithAnotherAmount_is409() {
    String key = UUID.randomUUID().toString();
    charge(key, "tok_approve");

    ResponseEntity<Map<String, Object>> changed =
        charge(key, "10.00", "EUR", "tok_approve");

    assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void negativeAmount_is400() {
    ResponseEntity<Map<String, Object>> response =
        charge("k", "-1.00", "EUR", "tok_approve");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void amountWithThreeDecimals_is400() {
    ResponseEntity<Map<String, Object>> response =
        charge("k", "1.001", "EUR", "tok_approve");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void otherCurrency_is400() {
    ResponseEntity<Map<String, Object>> response =
        charge("k", "9.00", "USD", "tok_approve");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void missingIdempotencyKey_is400() {
    ResponseEntity<Map<String, Object>> response =
        charge(Map.of("amount", "9.00", "currency", "EUR", "token", "tok_approve"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void health_isUp() {
    ResponseEntity<Map<String, Object>> response =
        http.get().uri("/health").retrieve().toEntity(JSON);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("status", "UP");
  }
}
