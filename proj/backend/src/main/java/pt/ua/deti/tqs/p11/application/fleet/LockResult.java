package pt.ua.deti.tqs.p11.application.fleet;

/**
 * The answer of a vehicle to a lock command (simulator contract: COMPLETED, REJECTED or FAILED), or
 * the lack of one within the time the platform waits.
 */
public sealed interface LockResult {

  /** The vehicle is locked (or already was: commands are idempotent). */
  record Locked() implements LockResult {}

  /**
   * The vehicle answered but is not locked: REJECTED by a rule (e.g. NOT_AT_HUB, between hubs) or
   * FAILED. {@code reason} is the contract's code, never shown to the tourist as it is.
   */
  record Refused(String reason) implements LockResult {}

  /** No answer in time. Whether it locked is unknown; sending lock again is safe. */
  record NoAnswer() implements LockResult {}
}
