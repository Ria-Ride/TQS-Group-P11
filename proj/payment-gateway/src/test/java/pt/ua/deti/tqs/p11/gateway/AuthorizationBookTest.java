package pt.ua.deti.tqs.p11.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Deposits: an amount is held (authorized), then either taken in part or in full (captured, the
 * rest released at once) or given back whole (released). Every step is safe to repeat.
 */
class AuthorizationBookTest {

  private final AuthorizationBook book = new AuthorizationBook();

  private static AuthorizationRequest request(String key, String amount, String token) {
    return new AuthorizationRequest(key, new BigDecimal(amount), "EUR", token);
  }

  private Authorization authorized(String key) {
    return book.authorize(request(key, "30.00", "tok_approve"));
  }

  // ------------------------------------------------------------- authorize

  @Test
  void authorize_approvingCard_holdsTheAmount() {
    Authorization authorization = authorized("k-1");

    assertThat(authorization.status()).isEqualTo(AuthorizationStatus.AUTHORIZED);
    assertThat(authorization.amount()).isEqualByComparingTo("30.00");
    assertThat(authorization.id()).startsWith("au_");
    assertThat(authorization.late()).isFalse();
  }

  @Test
  void authorize_decliningCard_isDeclined() {
    Authorization authorization = book.authorize(request("k-1", "30.00", "tok_decline"));

    assertThat(authorization.status()).isEqualTo(AuthorizationStatus.DECLINED);
    assertThat(authorization.reason()).isEqualTo("card_declined");
  }

  /**
   * The worst case of a real gateway: the money is held, but the answer is lost. The caller times
   * out and must release the deposit with the same key.
   */
  @Test
  void authorize_timeoutCard_holdsTheAmountButAnswersLate() {
    Authorization authorization = book.authorize(request("k-1", "30.00", "tok_timeout"));

    assertThat(authorization.status()).isEqualTo(AuthorizationStatus.AUTHORIZED);
    assertThat(authorization.late()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"tok_unknown", "4242424242424242", ""})
  void authorize_unknownCard_isDeclinedAsInvalid(String token) {
    Authorization authorization = book.authorize(request("k-1", "30.00", token));

    assertThat(authorization.status()).isEqualTo(AuthorizationStatus.DECLINED);
    assertThat(authorization.reason()).isEqualTo("invalid_token");
  }

  @Test
  void authorize_sameKeyAgain_returnsTheSameAuthorizationAndHoldsOnce() {
    Authorization first = authorized("k-1");

    Authorization again = authorized("k-1");

    assertThat(again).isEqualTo(first);
    assertThat(book.size()).isEqualTo(1);
  }

  @Test
  void authorize_sameKeyWithAnotherAmount_isRefused() {
    authorized("k-1");
    AuthorizationRequest changed = request("k-1", "50.00", "tok_approve");

    assertThatThrownBy(() -> book.authorize(changed))
        .isInstanceOf(IdempotencyKeyReusedException.class);
  }

  // ------------------------------------------------------------- capture

  @Test
  void capture_partOfTheAmount_takesItAndReleasesTheRest() {
    authorized("k-1");

    Authorization captured = book.capture("k-1", new BigDecimal("4.00"));

    assertThat(captured.status()).isEqualTo(AuthorizationStatus.CAPTURED);
    assertThat(captured.captured()).isEqualByComparingTo("4.00");
    assertThat(captured.released()).isEqualByComparingTo("26.00");
  }

  @Test
  void capture_theWholeAmount_releasesNothing() {
    authorized("k-1");

    Authorization captured = book.capture("k-1", new BigDecimal("30.00"));

    assertThat(captured.captured()).isEqualByComparingTo("30.00");
    assertThat(captured.released()).isEqualByComparingTo("0.00");
  }

  @Test
  void capture_zero_releasesEverything() {
    authorized("k-1");

    Authorization captured = book.capture("k-1", BigDecimal.ZERO);

    assertThat(captured.captured()).isEqualByComparingTo("0.00");
    assertThat(captured.released()).isEqualByComparingTo("30.00");
  }

  @Test
  void capture_moreThanWasHeld_isRefused() {
    authorized("k-1");
    BigDecimal tooMuch = new BigDecimal("30.01");

    assertThatThrownBy(() -> book.capture("k-1", tooMuch))
        .isInstanceOf(CaptureExceedsAuthorizationException.class);
  }

  @Test
  void capture_sameAmountAgain_returnsTheSameCapture() {
    authorized("k-1");
    Authorization first = book.capture("k-1", new BigDecimal("4.00"));

    Authorization again = book.capture("k-1", new BigDecimal("4.00"));

    assertThat(again).isEqualTo(first);
  }

  @Test
  void capture_anotherAmountAfterACapture_isRefused() {
    authorized("k-1");
    book.capture("k-1", new BigDecimal("4.00"));
    BigDecimal other = new BigDecimal("6.00");

    assertThatThrownBy(() -> book.capture("k-1", other))
        .isInstanceOf(AuthorizationStateConflictException.class);
  }

  @Test
  void capture_afterARelease_isRefused() {
    authorized("k-1");
    book.release("k-1");
    BigDecimal amount = new BigDecimal("4.00");

    assertThatThrownBy(() -> book.capture("k-1", amount))
        .isInstanceOf(AuthorizationStateConflictException.class);
  }

  @Test
  void capture_declinedAuthorization_isRefused() {
    book.authorize(request("k-1", "30.00", "tok_decline"));
    BigDecimal amount = new BigDecimal("4.00");

    assertThatThrownBy(() -> book.capture("k-1", amount))
        .isInstanceOf(AuthorizationStateConflictException.class);
  }

  @Test
  void capture_unknownKey_isNotFound() {
    BigDecimal amount = new BigDecimal("4.00");

    assertThatThrownBy(() -> book.capture("nope", amount))
        .isInstanceOf(AuthorizationNotFoundException.class);
  }

  // ------------------------------------------------------------- release

  @Test
  void release_authorized_givesBackTheWholeAmount() {
    authorized("k-1");

    Authorization released = book.release("k-1");

    assertThat(released.status()).isEqualTo(AuthorizationStatus.RELEASED);
    assertThat(released.released()).isEqualByComparingTo("30.00");
  }

  @Test
  void release_twice_returnsTheSameRelease() {
    authorized("k-1");
    Authorization first = book.release("k-1");

    Authorization again = book.release("k-1");

    assertThat(again).isEqualTo(first);
  }

  /** Nothing was held, so there is nothing to give back and nothing to refuse. */
  @Test
  void release_declinedAuthorization_staysDeclined() {
    book.authorize(request("k-1", "30.00", "tok_decline"));

    Authorization released = book.release("k-1");

    assertThat(released.status()).isEqualTo(AuthorizationStatus.DECLINED);
  }

  @Test
  void release_afterATimeout_givesBackTheHeldAmount() {
    book.authorize(request("k-1", "30.00", "tok_timeout"));

    Authorization released = book.release("k-1");

    assertThat(released.status()).isEqualTo(AuthorizationStatus.RELEASED);
  }

  @Test
  void release_afterACapture_isRefused() {
    authorized("k-1");
    book.capture("k-1", new BigDecimal("4.00"));

    assertThatThrownBy(() -> book.release("k-1"))
        .isInstanceOf(AuthorizationStateConflictException.class);
  }

  @Test
  void release_unknownKey_isNotFound() {
    assertThatThrownBy(() -> book.release("nope"))
        .isInstanceOf(AuthorizationNotFoundException.class);
  }
}