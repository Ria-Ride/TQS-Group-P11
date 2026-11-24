package pt.ua.deti.tqs.p11.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The test card decides the answer, and a repeated request gets the same answer once. */
class ChargeBookTest {

  private final ChargeBook book = new ChargeBook();

  private static ChargeRequest request(String key, String amount, String token) {
    return new ChargeRequest(key, new BigDecimal(amount), "EUR", token);
  }

  @Test
  void charge_approvingCard_isApprovedForTheAmount() {
    Charge charge = book.charge(request("k-1", "9.00", "tok_approve"));

    assertThat(charge.status()).isEqualTo(ChargeStatus.APPROVED);
    assertThat(charge.amount()).isEqualByComparingTo("9.00");
    assertThat(charge.id()).startsWith("ch_");
  }

  @Test
  void charge_decliningCard_isDeclined() {
    Charge charge = book.charge(request("k-1", "9.00", "tok_decline"));

    assertThat(charge.status()).isEqualTo(ChargeStatus.DECLINED);
    assertThat(charge.reason()).isEqualTo("card_declined");
  }

  /** The timeout card answers late; what it answers then is a decline, so no money is taken. */
  @Test
  void charge_timeoutCard_isDeclinedAndMarkedLate() {
    Charge charge = book.charge(request("k-1", "9.00", "tok_timeout"));

    assertThat(charge.status()).isEqualTo(ChargeStatus.DECLINED);
    assertThat(charge.reason()).isEqualTo("timeout");
    assertThat(charge.late()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"tok_unknown", "4242424242424242", ""})
  void charge_unknownCard_isDeclinedAsInvalid(String token) {
    Charge charge = book.charge(request("k-1", "9.00", token));

    assertThat(charge.status()).isEqualTo(ChargeStatus.DECLINED);
    assertThat(charge.reason()).isEqualTo("invalid_token");
  }

  @Test
  void charge_sameKeyAgain_returnsTheFirstChargeAndChargesOnce() {
    Charge first = book.charge(request("k-1", "9.00", "tok_approve"));

    Charge again = book.charge(request("k-1", "9.00", "tok_approve"));

    assertThat(again).isEqualTo(first);
    assertThat(book.size()).isEqualTo(1);
  }

  @Test
  void charge_differentKeys_areDifferentCharges() {
    Charge first = book.charge(request("k-1", "9.00", "tok_approve"));

    Charge second = book.charge(request("k-2", "9.00", "tok_approve"));

    assertThat(second.id()).isNotEqualTo(first.id());
  }

  @Test
  void charge_sameKeyWithAnotherAmount_isRefused() {
    book.charge(request("k-1", "9.00", "tok_approve"));
    ChargeRequest changed = request("k-1", "10.00", "tok_approve");

    assertThatThrownBy(() -> book.charge(changed))
        .isInstanceOf(IdempotencyKeyReusedException.class);
  }
}
