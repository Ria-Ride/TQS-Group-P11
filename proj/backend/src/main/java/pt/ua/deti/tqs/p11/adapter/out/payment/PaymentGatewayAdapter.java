package pt.ua.deti.tqs.p11.adapter.out.payment;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.p11.application.billing.ChargeRequest;
import pt.ua.deti.tqs.p11.application.billing.ChargeResult;
import pt.ua.deti.tqs.p11.application.billing.PaymentPort;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/**
 * PaymentPort over the HTTP API of the simulated payment gateway (Adapter pattern, K2). It is the
 * only class that knows that API: POST /charges answers 201 when approved and 402 when declined.
 *
 * <p>Anything else counts as {@link ChargeResult.TimedOut}: no answer within the read timeout, an
 * unreachable gateway, or an unexpected status. In those cases we cannot know whether money was
 * taken, so the charge is never treated as paid; our payment id is the idempotency key, so asking
 * again never charges twice. The payment token is never logged.
 */
public class PaymentGatewayAdapter implements PaymentPort {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayAdapter.class);
  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

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
    Map<String, Object> body =
        Map.of(
            "idempotencyKey", request.paymentId().toString(),
            "amount", request.amount().toString(),
            "currency", Money.CURRENCY,
            "token", request.paymentToken());
    try {
      return http.post()
          .uri("/charges")
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .exchange((sent, response) -> {
            HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
            if (status == HttpStatus.CREATED) {
              return new ChargeResult.Approved(field(response.bodyTo(JSON), "id"));
            }
            if (status == HttpStatus.PAYMENT_REQUIRED) {
              return new ChargeResult.Declined(field(response.bodyTo(JSON), "reason"));
            }
            LOG.warn(
                "Payment {}: unexpected gateway status {}",
                request.paymentId(),
                response.getStatusCode());
            return new ChargeResult.TimedOut();
          });
    } catch (ResourceAccessException e) {
      LOG.warn("Payment {}: no answer from the gateway ({})", request.paymentId(), e.getMessage());
      return new ChargeResult.TimedOut();
    }
  }

  private static String field(Map<String, Object> body, String name) {
    Object value = body == null ? null : body.get(name);
    return value == null ? "" : value.toString();
  }
}
