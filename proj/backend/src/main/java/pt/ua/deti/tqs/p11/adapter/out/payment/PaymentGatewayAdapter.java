package pt.ua.deti.tqs.p11.adapter.out.payment;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import pt.ua.deti.tqs.p11.application.billing.ChargeRequest;
import pt.ua.deti.tqs.p11.application.billing.ChargeResult;
import pt.ua.deti.tqs.p11.application.billing.DepositRequest;
import pt.ua.deti.tqs.p11.application.billing.DepositResult;
import pt.ua.deti.tqs.p11.application.billing.PaymentPort;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/**
 * PaymentPort over the HTTP API of the simulated payment gateway (Adapter pattern, K2). It is the
 * only class that knows that API:
 *
 * <ul>
 *   <li>POST /charges: 201 approved, 402 declined;
 *   <li>POST /authorizations: 201 held, 402 declined;
 *   <li>POST /authorizations/{paymentId}/capture: 200 taken (the rest given back); 400, 404 or 409
 *       when the deposit cannot be captured;
 *   <li>POST /authorizations/{paymentId}/release: 200 given back; 404 means nothing was held, so it
 *       is approved too; 409 when the deposit was already captured.
 * </ul>
 *
 * <p>Anything else counts as a time-out: no answer within the read timeout, an unreachable gateway,
 * or an unexpected status. In those cases we cannot know what the gateway did, so nothing is
 * treated as done; our payment id is the idempotency key, so repeating a call is safe. The payment
 * token is never logged.
 */
public class PaymentGatewayAdapter implements PaymentPort {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayAdapter.class);
  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};
  private static final String CONFLICT = "conflict";

  /** Turns a gateway status into a result, or null when the status is not expected. */
  @FunctionalInterface
  private interface Answer<T> {
    T from(HttpStatus status, ConvertibleClientHttpResponse response) throws IOException;
  }

  private final RestClient http;

  public PaymentGatewayAdapter(String baseUrl, Duration connectTimeout, Duration readTimeout) {
    JdkClientHttpRequestFactory requests =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(connectTimeout).build());
    requests.setReadTimeout(readTimeout);
    this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
  }

  @Override
  public ChargeResult charge(ChargeRequest request) {
    return post(
        "/charges",
        withCard(request.paymentId(), request.amount(), request.paymentToken()),
        request.paymentId(),
        (status, response) ->
            switch (status) {
              case CREATED -> new ChargeResult.Approved(field(response, "id"));
              case PAYMENT_REQUIRED -> new ChargeResult.Declined(field(response, "reason"));
              default -> null;
            },
        new ChargeResult.TimedOut());
  }

  @Override
  public DepositResult authorize(DepositRequest request) {
    return post(
        "/authorizations",
        withCard(request.paymentId(), request.amount(), request.paymentToken()),
        request.paymentId(),
        (status, response) ->
            switch (status) {
              case CREATED -> new DepositResult.Approved(field(response, "id"));
              case PAYMENT_REQUIRED -> new DepositResult.Declined(field(response, "reason"));
              default -> null;
            },
        new DepositResult.TimedOut());
  }

  @Override
  public DepositResult capture(UUID paymentId, Money amount) {
    return post(
        "/authorizations/" + paymentId + "/capture",
        Map.of("amount", amount.toString()),
        paymentId,
        (status, response) ->
            switch (status) {
              case OK -> new DepositResult.Approved(field(response, "id"));
              case CONFLICT -> new DepositResult.Declined(CONFLICT);
              case NOT_FOUND -> new DepositResult.Declined("unknown_deposit");
              case BAD_REQUEST -> new DepositResult.Declined("invalid_amount");
              default -> null;
            },
        new DepositResult.TimedOut());
  }

  @Override
  public DepositResult release(UUID paymentId) {
    return post(
        "/authorizations/" + paymentId + "/release",
        Map.of(),
        paymentId,
        (status, response) ->
            switch (status) {
              case OK -> new DepositResult.Approved(field(response, "id"));
              case NOT_FOUND -> new DepositResult.Approved("");
              case CONFLICT -> new DepositResult.Declined(CONFLICT);
              default -> null;
            },
        new DepositResult.TimedOut());
  }

  private static Map<String, Object> withCard(UUID paymentId, Money amount, String token) {
    return Map.of(
        "idempotencyKey",
        paymentId.toString(),
        "amount",
        amount.toString(),
        "currency",
        Money.CURRENCY,
        "token",
        token);
  }

  /**
   * Sends the request and turns the answer into a result. An unexpected status, no answer in time
   * or an unreachable gateway give {@code noAnswer}.
   */
  private <T> T post(String uri, Object body, UUID paymentId, Answer<T> answer, T noAnswer) {
    try {
      return http.post()
          .uri(uri)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .exchange((sent, response) -> {
            HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
            T result = status == null ? null : answer.from(status, response);
            if (result == null) {
              LOG.warn(
                  "Payment {}: unexpected gateway status {} on {}",
                  paymentId,
                  response.getStatusCode(),
                  uri);
              return noAnswer;
            }
            return result;
          });
    } catch (ResourceAccessException e) {
      LOG.warn("Payment {}: no answer from the gateway on {} ({})", paymentId, uri, e.getMessage());
      return noAnswer;
    }
  }

  private static String field(ConvertibleClientHttpResponse response, String name) {
    Map<String, Object> body = response.bodyTo(JSON);
    Object value = body == null ? null : body.get(name);
    return value == null ? "" : value.toString();
  }
}
