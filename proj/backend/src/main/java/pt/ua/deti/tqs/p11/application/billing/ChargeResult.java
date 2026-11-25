package pt.ua.deti.tqs.p11.application.billing;

/**
 * The three answers a charge can get (BR10). Only {@link Approved} means money was taken; a pass is
 * issued only then.
 */
public sealed interface ChargeResult {

  /** The money was taken. {@code reference} is the gateway's id of the charge. */
  record Approved(String reference) implements ChargeResult {}

  /** The gateway refused. {@code reason} is its code, such as card_declined. */
  record Declined(String reason) implements ChargeResult {}

  /**
   * No usable answer in time: the gateway was too slow, unreachable or failed. The charge is
   * treated as not paid; retrying with the same payment id would not charge twice.
   */
  record TimedOut() implements ChargeResult {}
}
