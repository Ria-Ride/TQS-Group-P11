package pt.ua.deti.tqs.p11.domain.booking;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The right to use one vehicle (Product Specs 3.1, 3.3). Immutable: every transition returns a new
 * Booking, and the constructor checks that the state has exactly the fields it needs, so an invalid
 * Booking cannot exist. startedAt is set from Active on; endedAt and endHubId only when Completed;
 * cancelReason only when Cancelled.
 *
 * @param depositReference the gateway's reference of the authorized deposit (BR11): a booking is
 *     created only after its deposit is authorized
 */
public record Booking(
    UUID id,
    BookingTerms terms,
    BookingRules rules,
    String depositReference,
    BookingState state,
    Instant startedAt,
    Instant endedAt,
    UUID endHubId,
    String cancelReason) {

  public Booking {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(terms, "terms");
    Objects.requireNonNull(rules, "rules");
    Objects.requireNonNull(depositReference, "depositReference");
    Objects.requireNonNull(state, "state");
    if (depositReference.isBlank()) {
      throw new IllegalArgumentException("A booking needs the reference of its deposit");
    }
    boolean started = state == BookingState.ACTIVE || state == BookingState.COMPLETED;
    boolean completed = state == BookingState.COMPLETED;
    requireOnlyIf(startedAt != null, started, state, "a start time");
    requireOnlyIf(endedAt != null, completed, state, "an end time");
    requireOnlyIf(endHubId != null, completed, state, "an end hub");
    requireOnlyIf(cancelReason != null, state == BookingState.CANCELLED, state, "a reason");
    if (cancelReason != null && cancelReason.isBlank()) {
      throw new IllegalArgumentException("A cancelled booking needs a reason");
    }
    if (endedAt != null && endedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException(
          "A booking cannot end before it starts: %s to %s".formatted(startedAt, endedAt));
    }
  }

  /** US04: an immediate start is Active from its planned start, which is now. */
  public static Booking startNow(
      UUID id, BookingTerms terms, BookingRules rules, String depositReference) {
    return new Booking(
        id,
        terms,
        rules,
        depositReference,
        BookingState.ACTIVE,
        terms.plannedStart(),
        null,
        null,
        null);
  }

  /** US05: a booking for a later start holds the vehicle until it is started or ends. */
  public static Booking reserve(
      UUID id, BookingTerms terms, BookingRules rules, String depositReference) {
    return new Booking(
        id, terms, rules, depositReference, BookingState.RESERVED, null, null, null, null);
  }

  /** Whether it may start now (start window, BR7) is checked by the caller, which has the clock. */
  public Booking start(Instant at) {
    Objects.requireNonNull(at, "at");
    return with(state.after(BookingAction.START), at, null, null, null);
  }

  /** Whether the hub has a free slot (BR14) is checked by the caller, which knows the hub. */
  public Booking complete(UUID hubId, Instant at) {
    Objects.requireNonNull(hubId, "hubId");
    Objects.requireNonNull(at, "at");
    return with(state.after(BookingAction.COMPLETE), startedAt, at, hubId, null);
  }

  public Booking cancel(String reason) {
    Objects.requireNonNull(reason, "reason");
    return with(state.after(BookingAction.CANCEL), null, null, null, reason);
  }

  public Booking expire() {
    return with(state.after(BookingAction.EXPIRE), null, null, null, null);
  }

  private Booking with(
      BookingState next, Instant started, Instant ended, UUID endHub, String reason) {
    return new Booking(id, terms, rules, depositReference, next, started, ended, endHub, reason);
  }

  /** The field must be there exactly when the state needs it. */
  private static void requireOnlyIf(
      boolean present, boolean needed, BookingState state, String field) {
    if (present != needed) {
      throw new IllegalArgumentException(
          needed
              ? "A %s booking needs %s".formatted(state, field)
              : "A %s booking cannot have %s".formatted(state, field));
    }
  }
}
