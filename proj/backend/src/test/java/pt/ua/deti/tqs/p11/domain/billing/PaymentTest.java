package pt.ua.deti.tqs.p11.domain.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Money movements, kept whether or not they went through (payment lifecycle, Product Specs 3.3):
 * the charge of a pass (BR10) and the deposit of a booking (BR11, BR13).
 */
class PaymentTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID ANA = UUID.randomUUID();
  private static final UUID PASS = UUID.randomUUID();
  private static final UUID BOOKING = UUID.randomUUID();
  private static final Money PRICE = Money.of("9.00");
  private static final Money DEPOSIT = Money.of("30.00");
  private static final Instant AT = Instant.parse("2026-11-24T10:15:30Z");

  // ------------------------------------------------------------- passes (US03)

  @Test
  void captured_keepsTheGatewayReferenceAndThePass() {
    Payment payment = Payment.captured(ID, ANA, PRICE, "ch_1", PASS, AT);

    assertThat(payment.kind()).isEqualTo(PaymentKind.PASS);
    assertThat(payment.state()).isEqualTo(PaymentState.CAPTURED);
    assertThat(payment.gatewayReference()).contains("ch_1");
    assertThat(payment.passId()).contains(PASS);
    assertThat(payment.failureReason()).isEmpty();
    assertThat(payment.bookingId()).isEmpty();
  }

  @Test
  void declined_keepsTheReasonAndHasNoPass() {
    Payment payment = Payment.declined(ID, ANA, PRICE, "card_declined", AT);

    assertThat(payment.kind()).isEqualTo(PaymentKind.PASS);
    assertThat(payment.state()).isEqualTo(PaymentState.DECLINED);
    assertThat(payment.failureReason()).contains("card_declined");
    assertThat(payment.passId()).isEmpty();
    assertThat(payment.gatewayReference()).isEmpty();
  }

  @Test
  void new_capturedWithoutAPass_isRefused() {
    assertThatThrownBy(() ->
            new Payment(
                ID,
                ANA,
                PaymentKind.PASS,
                PRICE,
                PaymentState.CAPTURED,
                "ch_1",
                null,
                null,
                null,
                AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_capturedWithoutAGatewayReference_isRefused() {
    assertThatThrownBy(() ->
            new Payment(
                ID,
                ANA,
                PaymentKind.PASS,
                PRICE,
                PaymentState.CAPTURED,
                null,
                null,
                PASS,
                null,
                AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** BR10: no pass is issued for a payment that was not approved. */
  @Test
  void new_declinedWithAPass_isRefused() {
    assertThatThrownBy(() ->
            new Payment(
                ID,
                ANA,
                PaymentKind.PASS,
                PRICE,
                PaymentState.DECLINED,
                null,
                "timeout",
                PASS,
                null,
                AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_declinedWithoutAReason_isRefused() {
    assertThatThrownBy(() ->
            new Payment(
                ID,
                ANA,
                PaymentKind.PASS,
                PRICE,
                PaymentState.DECLINED,
                null,
                null,
                null,
                null,
                AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** A pass is charged at once: it is never only authorized. */
  @Test
  void new_authorizedPassPayment_isRefused() {
    assertThatThrownBy(() ->
            new Payment(
                ID,
                ANA,
                PaymentKind.PASS,
                PRICE,
                PaymentState.AUTHORIZED,
                "ch_1",
                null,
                null,
                null,
                AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ------------------------------------------------------------- deposits (US04)

  /** BR11: the deposit is held and belongs to the booking it made possible. */
  @Test
  void depositAuthorized_keepsTheReferenceAndTheBooking() {
    Payment deposit = Payment.depositAuthorized(ID, ANA, DEPOSIT, "au_1", BOOKING, AT);

    assertThat(deposit.kind()).isEqualTo(PaymentKind.DEPOSIT);
    assertThat(deposit.state()).isEqualTo(PaymentState.AUTHORIZED);
    assertThat(deposit.amount()).isEqualTo(DEPOSIT);
    assertThat(deposit.gatewayReference()).contains("au_1");
    assertThat(deposit.bookingId()).contains(BOOKING);
    assertThat(deposit.passId()).isEmpty();
    assertThat(deposit.failureReason()).isEmpty();
  }

  /** BR11: declined or timed out, so no booking was created. */
  @Test
  void depositDeclined_keepsTheReasonAndHasNoBooking() {
    Payment deposit = Payment.depositDeclined(ID, ANA, DEPOSIT, "timeout", AT);

    assertThat(deposit.kind()).isEqualTo(PaymentKind.DEPOSIT);
    assertThat(deposit.state()).isEqualTo(PaymentState.DECLINED);
    assertThat(deposit.failureReason()).contains("timeout");
    assertThat(deposit.bookingId()).isEmpty();
  }

  /** Held, then given back because the booking could not be created (BR3: another one won). */
  @Test
  void depositReleased_withoutABooking_isAllowed() {
    Payment deposit = Payment.depositReleased(ID, ANA, DEPOSIT, "au_1", null, AT);

    assertThat(deposit.state()).isEqualTo(PaymentState.RELEASED);
    assertThat(deposit.gatewayReference()).contains("au_1");
    assertThat(deposit.bookingId()).isEmpty();
  }

  @Test
  void new_authorizedDepositWithoutABooking_isRefused() {
    assertThatThrownBy(() -> Payment.depositAuthorized(ID, ANA, DEPOSIT, "au_1", null, AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_authorizedDepositWithoutAReference_isRefused() {
    assertThatThrownBy(() -> Payment.depositAuthorized(ID, ANA, DEPOSIT, null, BOOKING, AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_depositWithAPass_isRefused() {
    assertThatThrownBy(() ->
            new Payment(
                ID,
                ANA,
                PaymentKind.DEPOSIT,
                DEPOSIT,
                PaymentState.AUTHORIZED,
                "au_1",
                null,
                PASS,
                BOOKING,
                AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_declinedDepositWithABooking_isRefused() {
    assertThatThrownBy(() ->
            new Payment(
                ID,
                ANA,
                PaymentKind.DEPOSIT,
                DEPOSIT,
                PaymentState.DECLINED,
                null,
                "card_declined",
                null,
                BOOKING,
                AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** A captured deposit needs the amount taken, which US06 adds with the overtime charge. */
  @Test
  void new_capturedDeposit_isRefusedUntilTheCaptureIsModelled() {
    assertThatThrownBy(() ->
            new Payment(
                ID,
                ANA,
                PaymentKind.DEPOSIT,
                DEPOSIT,
                PaymentState.CAPTURED,
                "au_1",
                null,
                null,
                BOOKING,
                AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ------------------------------------------------------------- every field rule

  private static String orNull(String cell) {
    return "-".equals(cell) ? null : cell;
  }

  private static UUID idOrNull(String cell, UUID id) {
    return "-".equals(cell) ? null : id;
  }

  /**
   * Each row breaks exactly one field rule of its kind and state ("-" is no value), so every part
   * of every rule is shown to matter: removing one would let its row through.
   */
  @ParameterizedTest(name = "{0} {1}: {6}")
  @CsvSource({
    "PASS,    CAPTURED,   ch_1, oops, pass, -,       a captured pass has no reason",
    "PASS,    CAPTURED,   ch_1, -,    pass, booking, a pass payment has no booking",
    "PASS,    RELEASED,   ch_1, -,    -,    -,       a pass is never released",
    "DEPOSIT, AUTHORIZED, au_1, oops, -,    booking, a held deposit has no reason",
    "DEPOSIT, RELEASED,   -,    -,    -,    booking, a released deposit has its reference",
    "DEPOSIT, RELEASED,   au_1, oops, -,    booking, a released deposit has no reason",
    "DEPOSIT, DECLINED,   -,    -,    -,    -,       a declined deposit has a reason"
  })
  void new_fieldsThatDoNotFitTheKindAndState_areRefused(
      PaymentKind kind,
      PaymentState state,
      String reference,
      String reason,
      String pass,
      String booking,
      String rule) {
    Money amount = kind == PaymentKind.PASS ? PRICE : DEPOSIT;
    String ref = orNull(reference);
    String why = orNull(reason);
    UUID passId = idOrNull(pass, PASS);
    UUID bookingId = idOrNull(booking, BOOKING);

    assertThatThrownBy(
            () -> new Payment(ID, ANA, kind, amount, state, ref, why, passId, bookingId, AT))
        .as(rule)
        .isInstanceOf(IllegalArgumentException.class);
  }
}
