package pt.ua.deti.tqs.p11.application.billing;

/** The three answers each step of a deposit can get: authorize, capture, release (BR11, BR13). */
public sealed interface DepositResult {

  /**
   * The step was done: the deposit is held, taken (the rest given back) or given back. {@code
   * reference} is the gateway's id of the deposit; empty when a release found nothing held.
   */
  record Approved(String reference) implements DepositResult {}

  /**
   * The gateway refused: a declined card, or a step that no longer fits the deposit (such as
   * capturing after a release). {@code reason} is a code, never shown to the tourist as it is.
   */
  record Declined(String reason) implements DepositResult {}

  /**
   * No usable answer in time. Whether the step was done is unknown; repeating it, or releasing,
   * with the same payment id is safe.
   */
  record TimedOut() implements DepositResult {}
}
