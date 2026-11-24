package pt.ua.deti.tqs.p11.domain.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A charge of a pass, kept whether or not it was approved (payment lifecycle, BR10). */
class PaymentTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID ANA = UUID.randomUUID();
  private static final UUID PASS = UUID.randomUUID();
  private static final Money PRICE = Money.of("9.00");
  private static final Instant AT = Instant.parse("2026-11-24T10:15:30Z");

  @Test
  void captured_keepsTheGatewayReferenceAndThePass() {
    Payment payment = Payment.captured(ID, ANA, PRICE, "ch_1", PASS, AT);

    assertThat(payment.state()).isEqualTo(PaymentState.CAPTURED);
    assertThat(payment.gatewayReference()).contains("ch_1");
    assertThat(payment.passId()).contains(PASS);
    assertThat(payment.failureReason()).isEmpty();
  }

  @Test
  void declined_keepsTheReasonAndHasNoPass() {
    Payment payment = Payment.declined(ID, ANA, PRICE, "card_declined", AT);

    assertThat(payment.state()).isEqualTo(PaymentState.DECLINED);
    assertThat(payment.failureReason()).contains("card_declined");
    assertThat(payment.passId()).isEmpty();
    assertThat(payment.gatewayReference()).isEmpty();
  }

  @Test
  void new_capturedWithoutAPass_isRefused() {
    assertThatThrownBy(
            () -> new Payment(ID, ANA, PRICE, PaymentState.CAPTURED, "ch_1", null, null, AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_capturedWithoutAGatewayReference_isRefused() {
    assertThatThrownBy(
            () -> new Payment(ID, ANA, PRICE, PaymentState.CAPTURED, null, null, PASS, AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** BR10: no pass is issued for a payment that was not approved. */
  @Test
  void new_declinedWithAPass_isRefused() {
    assertThatThrownBy(
            () -> new Payment(ID, ANA, PRICE, PaymentState.DECLINED, null, "timeout", PASS, AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_declinedWithoutAReason_isRefused() {
    assertThatThrownBy(
            () -> new Payment(ID, ANA, PRICE, PaymentState.DECLINED, null, null, null, AT))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
