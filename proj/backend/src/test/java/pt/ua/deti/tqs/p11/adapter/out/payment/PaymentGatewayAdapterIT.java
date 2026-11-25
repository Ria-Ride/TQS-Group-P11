package pt.ua.deti.tqs.p11.adapter.out.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.application.billing.ChargeRequest;
import pt.ua.deti.tqs.p11.application.billing.ChargeResult;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.support.PaymentGatewayContainer;

/**
 * The payment adapter against the simulated gateway in a container (KAN-96): approved, declined and
 * timed out are three distinct results (BR10).
 */
class PaymentGatewayAdapterIT {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(1);
  private static final PaymentGatewayContainer GATEWAY = new PaymentGatewayContainer();

  private static PaymentGatewayAdapter adapter;

  @BeforeAll
  static void startTheGateway() {
    GATEWAY.start();
    adapter = new PaymentGatewayAdapter(GATEWAY.baseUrl(), CONNECT_TIMEOUT, READ_TIMEOUT);
  }

  @AfterAll
  static void stopTheGateway() {
    GATEWAY.stop();
  }

  private static ChargeRequest charge(String card) {
    return new ChargeRequest(UUID.randomUUID(), Money.of("9.00"), card);
  }

  @Test
  void charge_approvingCard_isApprovedWithTheGatewayReference() {
    ChargeResult result = adapter.charge(charge("tok_approve"));

    assertThat(result)
        .isInstanceOfSatisfying(
            ChargeResult.Approved.class,
            approved -> assertThat(approved.reference()).startsWith("ch_"));
  }

  @Test
  void charge_decliningCard_isDeclinedWithTheReason() {
    assertThat(adapter.charge(charge("tok_decline")))
        .isEqualTo(new ChargeResult.Declined("card_declined"));
  }

  @Test
  void charge_unknownCard_isDeclined() {
    assertThat(adapter.charge(charge("tok_nonsense")))
        .isEqualTo(new ChargeResult.Declined("invalid_token"));
  }

  /** The gateway holds the answer for 5 s; the adapter gives up after its 1 s read timeout. */
  @Test
  void charge_noAnswerInTime_isTimedOutAfterTheReadTimeout() {
    Instant start = Instant.now();

    ChargeResult result = adapter.charge(charge("tok_timeout"));

    assertThat(result).isInstanceOf(ChargeResult.TimedOut.class);
    assertThat(Duration.between(start, Instant.now()))
        .isLessThan(PaymentGatewayContainer.TIMEOUT_DELAY);
  }

  @Test
  void charge_gatewayUnreachable_isTimedOut() {
    PaymentGatewayAdapter nowhere =
        new PaymentGatewayAdapter("http://localhost:9", CONNECT_TIMEOUT, READ_TIMEOUT);

    assertThat(nowhere.charge(charge("tok_approve"))).isInstanceOf(ChargeResult.TimedOut.class);
  }

  /** Our payment id is the idempotency key: a retry gets the first charge, not a second one. */
  @Test
  void charge_samePaymentIdTwice_isChargedOnce() {
    ChargeRequest request = charge("tok_approve");

    ChargeResult first = adapter.charge(request);
    ChargeResult again = adapter.charge(request);

    assertThat(again).isEqualTo(first);
  }
}
