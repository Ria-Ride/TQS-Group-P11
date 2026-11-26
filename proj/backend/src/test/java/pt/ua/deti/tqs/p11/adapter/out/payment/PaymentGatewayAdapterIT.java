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
import pt.ua.deti.tqs.p11.application.billing.DepositRequest;
import pt.ua.deti.tqs.p11.application.billing.DepositResult;

/**
 * The payment adapter against the simulated gateway in a container (KAN-96): approved, declined and
 * timed out are three distinct results, for charges (BR10) and for each step of a deposit (BR11,
 * BR13).
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

    // ------------------------------------------------------------- deposits (US04)

  private static final Money DEPOSIT = Money.of("30.00");

  private static DepositRequest deposit(String card) {
    return new DepositRequest(UUID.randomUUID(), DEPOSIT, card);
  }

  /** A deposit held on the approving card; returns its payment id. */
  private static UUID authorized() {
    DepositRequest request = deposit("tok_approve");
    assertThat(adapter.authorize(request))
        .as("setup: authorize")
        .isInstanceOf(DepositResult.Approved.class);
    return request.paymentId();
  }

  @Test
  void authorize_approvingCard_isApprovedWithTheGatewayReference() {
    DepositResult result = adapter.authorize(deposit("tok_approve"));

    assertThat(result)
        .isInstanceOfSatisfying(
            DepositResult.Approved.class,
            approved -> assertThat(approved.reference()).startsWith("au_"));
  }

  @Test
  void authorize_decliningCard_isDeclinedWithTheReason() {
    assertThat(adapter.authorize(deposit("tok_decline")))
        .isEqualTo(new DepositResult.Declined("card_declined"));
  }

  @Test
  void authorize_samePaymentIdTwice_holdsTheDepositOnce() {
    DepositRequest request = deposit("tok_approve");

    DepositResult first = adapter.authorize(request);
    DepositResult again = adapter.authorize(request);

    assertThat(again).isEqualTo(first);
  }

  /**
   * BR11: the gateway held the deposit but the answer came too late. The adapter gives up after
   * its read timeout; releasing with the same payment id gives the deposit back, which a capture
   * then proves by being refused.
   */
  @Test
  void authorize_noAnswerInTime_isTimedOut_andReleasingGivesTheDepositBack() {
    DepositRequest request = deposit("tok_timeout");
    Instant start = Instant.now();

    DepositResult result = adapter.authorize(request);

    assertThat(result).isInstanceOf(DepositResult.TimedOut.class);
    assertThat(Duration.between(start, Instant.now()))
        .isLessThan(PaymentGatewayContainer.TIMEOUT_DELAY);
    assertThat(adapter.release(request.paymentId())).isInstanceOf(DepositResult.Approved.class);
    assertThat(adapter.capture(request.paymentId(), Money.of("1.00")))
        .isInstanceOf(DepositResult.Declined.class);
  }

  /** BR13 (US06): a partial capture; the rest is released by the gateway at the same time. */
  @Test
  void capture_partOfTheDeposit_isApproved() {
    UUID payment = authorized();

    assertThat(adapter.capture(payment, Money.of("4.00")))
        .isInstanceOf(DepositResult.Approved.class);
  }

  @Test
  void capture_sameAmountTwice_isApprovedBothTimes() {
    UUID payment = authorized();

    DepositResult first = adapter.capture(payment, Money.of("4.00"));
    DepositResult again = adapter.capture(payment, Money.of("4.00"));

    assertThat(again).isEqualTo(first).isInstanceOf(DepositResult.Approved.class);
  }

  @Test
  void capture_afterARelease_isDeclined() {
    UUID payment = authorized();
    adapter.release(payment);

    assertThat(adapter.capture(payment, Money.of("4.00")))
        .isInstanceOf(DepositResult.Declined.class);
  }

  @Test
  void capture_moreThanTheDeposit_isDeclined() {
    UUID payment = authorized();

    assertThat(adapter.capture(payment, Money.of("30.01")))
        .isInstanceOf(DepositResult.Declined.class);
  }

  @Test
  void capture_unknownPayment_isDeclined() {
    assertThat(adapter.capture(UUID.randomUUID(), Money.of("4.00")))
        .isInstanceOf(DepositResult.Declined.class);
  }

  /** BR13 (US05): a cancelled or expired booking gives the whole deposit back. */
  @Test
  void release_heldDeposit_isApproved() {
    UUID payment = authorized();

    assertThat(adapter.release(payment)).isInstanceOf(DepositResult.Approved.class);
  }

  @Test
  void release_twice_isApprovedBothTimes() {
    UUID payment = authorized();
    adapter.release(payment);

    assertThat(adapter.release(payment)).isInstanceOf(DepositResult.Approved.class);
  }

  @Test
  void release_afterACapture_isDeclined() {
    UUID payment = authorized();
    adapter.capture(payment, Money.of("4.00"));

    assertThat(adapter.release(payment)).isInstanceOf(DepositResult.Declined.class);
  }

  /** The authorization never reached the gateway, so nothing is held: nothing to refuse either. */
  @Test
  void release_unknownPayment_isApproved() {
    assertThat(adapter.release(UUID.randomUUID())).isInstanceOf(DepositResult.Approved.class);
  }

  @Test
  void deposit_gatewayUnreachable_isTimedOutForEveryStep() {
    PaymentGatewayAdapter nowhere =
        new PaymentGatewayAdapter("http://localhost:9", CONNECT_TIMEOUT, READ_TIMEOUT);
    UUID payment = UUID.randomUUID();

    assertThat(nowhere.authorize(new DepositRequest(payment, DEPOSIT, "tok_approve")))
        .isInstanceOf(DepositResult.TimedOut.class);
    assertThat(nowhere.capture(payment, Money.of("4.00")))
        .isInstanceOf(DepositResult.TimedOut.class);
    assertThat(nowhere.release(payment)).isInstanceOf(DepositResult.TimedOut.class);
  }
}
