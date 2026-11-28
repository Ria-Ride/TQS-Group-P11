package pt.ua.deti.tqs.p11.domain.booking;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The right to use one vehicle (Product Specs 3.1, 3.3). Immutable: every transition returns a new
 * Booking, and the constructor checks that the state has exactly the fields it needs, so an invalid
 * Booking cannot exist. startedAt is set from Active on; endedAt and endHubId only when Completed;
 * cancelReason only when Cancelled; fullHubAttemptAt, the first refused end at a full hub (BR15),
 * only once started.
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
    String cancelReason,
    Instant fullHubAttemptAt) {

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
    if (fullHubAttemptAt != null && !started) {
      throw new IllegalArgumentException(
          "A %s booking cannot have a full-hub attempt".formatted(state));
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
        null,
        null);
  }

  /** US05: a booking for a later start holds the vehicle until it is started or ends. */
  public static Booking reserve(
      UUID id, BookingTerms terms, BookingRules rules, String depositReference) {
    return new Booking(
        id, terms, rules, depositReference, BookingState.RESERVED, null, null, null, null, null);
  }

  /**
   * BR7: a reservation is started from its planned start up to the end of its start window, both
   * included. The lifecycle is checked first, so a booking that is not Reserved is refused as such.
   *
   * @throws BookingTransitionNotAllowedException if the booking is not Reserved
   * @throws BookingRefusedException TOO_EARLY_TO_START or START_WINDOW_PASSED outside the window
   */
  public Booking start(Instant at) {
    Objects.requireNonNull(at, "at");
    BookingState next = state.after(BookingAction.START);
    if (at.isBefore(terms.plannedStart())) {
      throw new BookingRefusedException(BookingRefusal.TOO_EARLY_TO_START);
    }
    if (at.isAfter(startWindowEnd())) {
      throw new BookingRefusedException(BookingRefusal.START_WINDOW_PASSED);
    }
    return with(next, at, null, null, null);
  }

  /** BR7: the last moment a reservation can be started, kept with its rules (BR21). */
  public Instant startWindowEnd() {
    return terms.plannedStart().plus(rules.startWindow());
  }

  /** Whether the hub has a free slot (BR14) is checked by the caller, which knows the hub. */
  public Booking complete(UUID hubId, Instant at) {
    Objects.requireNonNull(hubId, "hubId");
    Objects.requireNonNull(at, "at");
    return with(state.after(BookingAction.COMPLETE), startedAt, at, hubId, null);
  }

  /** BR7: a Reserved booking not started by the end of its window is due to expire. */
  public boolean isOverdue(Instant at) {
    Objects.requireNonNull(at, "at");
    return state == BookingState.RESERVED && at.isAfter(startWindowEnd());
  }

  /**
   * BR15: an end at a full hub was refused at this moment. Only the first refusal is kept; the ride
   * goes on (Active) until it ends at another hub.
   *
   * @throws BookingTransitionNotAllowedException if the booking is not Active
   */
  public Booking recordFullHubAttempt(Instant at) {
    Objects.requireNonNull(at, "at");
    if (state != BookingState.ACTIVE) {
      throw new BookingTransitionNotAllowedException(state, BookingAction.COMPLETE);
    }
    if (fullHubAttemptAt != null) {
      return this;
    }
    return new Booking(
        id, terms, rules, depositReference, state, startedAt, endedAt, endHubId, cancelReason, at);
  }

  /**
   * BR12, BR15: until when an ended ride is charged. The real end; or, when the ride ended within
   * the repositioning window after a refused end at a full hub (the window's last second included),
   * the moment of that refusal, so the tourist does not pay for looking for a free slot. Empty
   * until the booking is Completed.
   */
  public Optional<Instant> chargedUntil() {
    if (state != BookingState.COMPLETED) {
      return Optional.empty();
    }
    if (fullHubAttemptAt != null
        && !endedAt.isAfter(fullHubAttemptAt.plus(rules.repositioningWindow()))) {
      return Optional.of(fullHubAttemptAt);
    }
    return Optional.of(endedAt);
  }

  /**
   * BR12, BR13: the overtime charge of an ended ride, from its planned end to {@link
   * #chargedUntil()}, and how the deposit pays it. Empty until the booking is Completed.
   */
  public Optional<Settlement> settlement() {
    return chargedUntil()
        .map(until -> Duration.between(terms.plannedEnd(), until))
        .map(late -> Settlement.of(Overtime.charge(rules, late), rules.deposit()));
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
    return new Booking(
        id, terms, rules, depositReference, next, started, ended, endHub, reason, fullHubAttemptAt);
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
