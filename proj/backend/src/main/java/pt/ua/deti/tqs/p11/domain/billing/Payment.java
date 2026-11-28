package pt.ua.deti.tqs.p11.domain.billing;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One money movement, kept whether or not it went through, so every purchase and deposit can be
 * traced. No card data is kept, only the gateway's reference. Which fields a payment has depends on
 * its kind and state:
 *
 * <ul>
 *   <li>pass, CAPTURED: reference and pass (BR10); pass, DECLINED: reason, no pass;
 *   <li>deposit, AUTHORIZED: reference and booking (BR11); deposit, RELEASED: reference, and the
 *       booking unless it was never created; deposit, DECLINED: reason, no booking.
 * </ul>
 *
 * <li>deposit, CAPTURED: reference, booking and the amount taken from it (BR13, US06).
 *
 * @param reference the gateway's id of the charge or deposit; null when nothing went through
 * @param reason why no money was taken or held (card_declined, timeout, ...)
 * @param pass the pass a pass payment paid for
 * @param booking the booking a deposit belongs to
 * @param captured how much of a captured deposit was taken; null for any other payment
 */
public record Payment(
    UUID id,
    UUID accountId,
    PaymentKind kind,
    Money amount,
    PaymentState state,
    String reference,
    String reason,
    UUID pass,
    UUID booking,
    Instant at,
    Money captured) {

  public Payment {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(accountId, "accountId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(at, "at");
    boolean valid =
        switch (kind) {
          case PASS ->
              booking == null
                  && captured == null
                  && passFieldsMatch(state, reference, reason, pass);
          case DEPOSIT ->
              pass == null
                  && depositFieldsMatch(state, reference, reason, booking, amount, captured);
        };
    if (!valid) {
      throw new IllegalArgumentException(
          "A %s payment in state %s does not have the fields that state needs"
              .formatted(kind, state));
    }
  }

  private static boolean passFieldsMatch(
      PaymentState state, String reference, String reason, UUID pass) {
    return switch (state) {
      case CAPTURED -> reference != null && pass != null && reason == null;
      case DECLINED -> reason != null && pass == null;
      case AUTHORIZED, RELEASED -> false;
    };
  }

  private static boolean depositFieldsMatch(
      PaymentState state,
      String reference,
      String reason,
      UUID booking,
      Money deposit,
      Money taken) {
    return switch (state) {
      case AUTHORIZED -> reference != null && booking != null && reason == null && taken == null;
      case RELEASED -> reference != null && reason == null && taken == null;
      case DECLINED -> reason != null && booking == null && taken == null;
      // BR13: something was taken, and never more than the deposit.
      case CAPTURED ->
          reference != null
              && booking != null
              && reason == null
              && taken != null
              && !taken.isZero()
              && taken.atMost(deposit).equals(taken);
    };
  }

  // ------------------------------------------------------------- passes (US03)

  public static Payment captured(
      UUID id, UUID accountId, Money amount, String reference, UUID passId, Instant at) {
    return new Payment(
        id,
        accountId,
        PaymentKind.PASS,
        amount,
        PaymentState.CAPTURED,
        reference,
        null,
        passId,
        null,
        at,
        null);
  }

  public static Payment declined(UUID id, UUID accountId, Money amount, String reason, Instant at) {
    return new Payment(
        id,
        accountId,
        PaymentKind.PASS,
        amount,
        PaymentState.DECLINED,
        null,
        reason,
        null,
        null,
        at,
        null);
  }

  // ------------------------------------------------------------- deposits (US04)

  /** BR11: the deposit is held and the booking it made possible exists. */
  public static Payment depositAuthorized(
      UUID id, UUID accountId, Money amount, String reference, UUID bookingId, Instant at) {
    return new Payment(
        id,
        accountId,
        PaymentKind.DEPOSIT,
        amount,
        PaymentState.AUTHORIZED,
        reference,
        null,
        null,
        bookingId,
        at,
        null);
  }

  /** BR11: declined or no answer in time, so no booking was created. */
  public static Payment depositDeclined(
      UUID id, UUID accountId, Money amount, String reason, Instant at) {
    return new Payment(
        id,
        accountId,
        PaymentKind.DEPOSIT,
        amount,
        PaymentState.DECLINED,
        null,
        reason,
        null,
        null,
        at,
        null);
  }

  /**
   * BR13: the deposit was given back whole.
   *
   * @param bookingId the booking, or null when it could not be created after the deposit was held
   */
  public static Payment depositReleased(
      UUID id, UUID accountId, Money amount, String reference, UUID bookingId, Instant at) {
    return new Payment(
        id,
        accountId,
        PaymentKind.DEPOSIT,
        amount,
        PaymentState.RELEASED,
        reference,
        null,
        null,
        bookingId,
        at,
        null);
  }

  /**
   * BR13: the overtime charge was taken from the deposit (at most all of it) and the gateway gave
   * the rest back in the same step.
   *
   * @param deposit the deposit that was held
   * @param taken how much of it was taken: more than zero, at most the deposit
   */
  public static Payment depositCaptured(
      UUID id,
      UUID accountId,
      Money deposit,
      Money taken,
      String reference,
      UUID bookingId,
      Instant at) {
    return new Payment(
        id,
        accountId,
        PaymentKind.DEPOSIT,
        deposit,
        PaymentState.CAPTURED,
        reference,
        null,
        null,
        bookingId,
        at,
        taken);
  }

  /** BR13: how much of a captured deposit was taken; empty for any other payment. */
  public Optional<Money> capturedAmount() {
    return Optional.ofNullable(captured);
  }

  public Optional<String> gatewayReference() {
    return Optional.ofNullable(reference);
  }

  public Optional<String> failureReason() {
    return Optional.ofNullable(reason);
  }

  public Optional<UUID> passId() {
    return Optional.ofNullable(pass);
  }

  public Optional<UUID> bookingId() {
    return Optional.ofNullable(booking);
  }
}
