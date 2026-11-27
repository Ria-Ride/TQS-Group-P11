package pt.ua.deti.tqs.p11.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.ua.deti.tqs.p11.domain.booking.BookingState.ACTIVE;
import static pt.ua.deti.tqs.p11.domain.booking.BookingState.CANCELLED;
import static pt.ua.deti.tqs.p11.domain.booking.BookingState.COMPLETED;
import static pt.ua.deti.tqs.p11.domain.booking.BookingState.EXPIRED;
import static pt.ua.deti.tqs.p11.domain.booking.BookingState.RESERVED;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/** The booking lifecycle (Product Specs 3.3) as seen through a Booking's own transitions. */
class BookingTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID ANA = UUID.randomUUID();
  private static final UUID BIKE = UUID.randomUUID();
  private static final UUID PASS = UUID.randomUUID();
  private static final UUID HUB_03 = UUID.randomUUID();
  private static final UUID HUB_05 = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
  private static final String DEPOSIT = "au_1";
  private static final BookingRules RULES =
      new BookingRules(
          Money.of("30.00"),
          Duration.ofMinutes(15),
          Duration.ofMinutes(10),
          Duration.ofMinutes(15),
          Money.of("2.00"));

  private static BookingTerms terms(Instant start, Duration length) {
    return new BookingTerms(ANA, BIKE, PASS, HUB_03, start, start.plus(length));
  }

  private static Booking active() {
    return Booking.startNow(ID, terms(NOW, Duration.ofMinutes(60)), RULES, DEPOSIT);
  }

  private static Booking reserved() {
    return Booking.reserve(
        ID, terms(NOW.plus(Duration.ofHours(1)), Duration.ofMinutes(60)), RULES, DEPOSIT);
  }

  private static void assertRefused(Runnable transition, BookingState from, BookingAction action) {
    assertThatThrownBy(transition::run)
        .isInstanceOfSatisfying(
            BookingTransitionNotAllowedException.class,
            e -> {
              assertThat(e.from()).isEqualTo(from);
              assertThat(e.action()).isEqualTo(action);
            });
  }

  // ------------------------------------------------------------- creation

  @Test
  void startNow_isActiveFromThePlannedStart() {
    Booking booking = active();

    assertThat(booking.state()).isEqualTo(ACTIVE);
    assertThat(booking.startedAt()).isEqualTo(NOW);
    assertThat(booking.endedAt()).isNull();
    assertThat(booking.endHubId()).isNull();
    assertThat(booking.cancelReason()).isNull();
    assertThat(booking.terms().startHubId()).isEqualTo(HUB_03);
    assertThat(booking.depositReference()).isEqualTo(DEPOSIT);
  }

  /** BR21: the booking carries the rules it was created with. */
  @Test
  void startNow_keepsTheRulesItWasCreatedWith() {
    assertThat(active().rules()).isEqualTo(RULES);
  }

  @Test
  void reserve_isReservedAndNotStarted() {
    Booking booking = reserved();

    assertThat(booking.state()).isEqualTo(RESERVED);
    assertThat(booking.startedAt()).isNull();
  }

  // ------------------------------------------------------------- allowed transitions

  @Test
  void start_reservedBooking_isActiveFromThatMoment() {
    Instant at = NOW.plus(Duration.ofMinutes(65));

    Booking started = reserved().start(at);

    assertThat(started.state()).isEqualTo(ACTIVE);
    assertThat(started.startedAt()).isEqualTo(at);
    assertThat(started.terms()).isEqualTo(reserved().terms());
  }

  // ------------------------------------------------------------- start window (BR7)

  /** reserved() starts at NOW + 1 h; its window is 15 minutes. */
  private static final Instant PLANNED_START = NOW.plus(Duration.ofHours(1));

  @ParameterizedTest(name = "started {0} s after the planned start")
  @ValueSource(longs = {0, 1, 899, 900})
  void start_fromThePlannedStartToTheEndOfTheWindow_isAccepted(long secondsLate) {
    Instant at = PLANNED_START.plusSeconds(secondsLate);

    assertThat(reserved().start(at).startedAt()).isEqualTo(at);
  }

  @Test
  void start_beforeThePlannedStart_isRefusedAsTooEarly() {
    Booking booking = reserved();
    Instant oneSecondEarly = PLANNED_START.minusSeconds(1);

    assertThatThrownBy(() -> booking.start(oneSecondEarly))
        .isInstanceOfSatisfying(
            BookingRefusedException.class,
            e -> assertThat(e.reason()).isEqualTo(BookingRefusal.TOO_EARLY_TO_START));
  }

  @Test
  void start_oneSecondAfterTheWindow_isRefusedAsTooLate() {
    Booking booking = reserved();
    Instant late = PLANNED_START.plus(Duration.ofMinutes(15)).plusSeconds(1);

    assertThatThrownBy(() -> booking.start(late))
        .isInstanceOfSatisfying(
            BookingRefusedException.class,
            e -> assertThat(e.reason()).isEqualTo(BookingRefusal.START_WINDOW_PASSED));
  }

  @ParameterizedTest(name = "{0} s after the planned start: overdue {1}")
  @CsvSource({"-60, false", "0, false", "900, false", "901, true", "86400, true"})
  void isOverdue_reservedBooking_onlyAfterItsWindow(long secondsLate, boolean overdue) {
    assertThat(reserved().isOverdue(PLANNED_START.plusSeconds(secondsLate))).isEqualTo(overdue);
  }

  @Test
  void isOverdue_bookingThatIsNotReserved_isNever() {
    Instant muchLater = PLANNED_START.plus(Duration.ofDays(1));

    assertThat(active().isOverdue(muchLater)).isFalse();
    assertThat(reserved().cancel("plans changed").isOverdue(muchLater)).isFalse();
  }

  // ------------------------------------------------------------- other transitions

  @Test
  void complete_activeBooking_recordsTheEndAndTheHub() {
    Instant at = NOW.plus(Duration.ofMinutes(70));

    Booking completed = active().complete(HUB_05, at);

    assertThat(completed.state()).isEqualTo(COMPLETED);
    assertThat(completed.endedAt()).isEqualTo(at);
    assertThat(completed.endHubId()).isEqualTo(HUB_05);
    assertThat(completed.startedAt()).isEqualTo(NOW);
  }

  @Test
  void complete_beforeItStarted_isRefused() {
    Booking booking = active();
    Instant before = NOW.minusSeconds(1);

    assertThatThrownBy(() -> booking.complete(HUB_05, before))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cancel_reservedBooking_keepsTheReason() {
    Booking cancelled = reserved().cancel("vehicle fault");

    assertThat(cancelled.state()).isEqualTo(CANCELLED);
    assertThat(cancelled.cancelReason()).isEqualTo("vehicle fault");
  }

  @ParameterizedTest(name = "reason \"{0}\"")
  @ValueSource(strings = {"", "   "})
  void cancel_withoutAReason_isRefused(String reason) {
    Booking booking = reserved();

    assertThatThrownBy(() -> booking.cancel(reason)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void expire_reservedBooking_isExpired() {
    assertThat(reserved().expire().state()).isEqualTo(EXPIRED);
  }

  // ------------------------------------------------------------- forbidden transitions

  @Test
  void start_activeBooking_isRefused() {
    Booking booking = active();

    assertRefused(() -> booking.start(NOW), ACTIVE, BookingAction.START);
  }

  @Test
  void complete_reservedBooking_isRefused() {
    Booking booking = reserved();

    assertRefused(() -> booking.complete(HUB_05, NOW), RESERVED, BookingAction.COMPLETE);
  }

  @Test
  void complete_completedBooking_isRefused() {
    Booking booking = active().complete(HUB_05, NOW.plus(Duration.ofMinutes(70)));

    assertRefused(
        () -> booking.complete(HUB_05, NOW.plus(Duration.ofMinutes(80))),
        COMPLETED,
        BookingAction.COMPLETE);
  }

  @Test
  void cancel_activeBooking_isRefused() {
    Booking booking = active();

    assertRefused(() -> booking.cancel("changed my mind"), ACTIVE, BookingAction.CANCEL);
  }

  @Test
  void expire_activeBooking_isRefused() {
    Booking booking = active();

    assertRefused(booking::expire, ACTIVE, BookingAction.EXPIRE);
  }

  // ------------------------------------------------------------- invariants

  @Test
  void new_activeWithoutAStartTime_isRefused() {
    BookingTerms terms = terms(NOW, Duration.ofMinutes(60));

    assertThatThrownBy(() -> new Booking(ID, terms, RULES, DEPOSIT, ACTIVE, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_reservedWithAStartTime_isRefused() {
    BookingTerms terms = terms(NOW, Duration.ofMinutes(60));

    assertThatThrownBy(
            () -> new Booking(ID, terms, RULES, DEPOSIT, RESERVED, NOW, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_completedWithoutAnEndHub_isRefused() {
    BookingTerms terms = terms(NOW, Duration.ofMinutes(60));
    Instant end = NOW.plus(Duration.ofMinutes(70));

    assertThatThrownBy(
            () -> new Booking(ID, terms, RULES, DEPOSIT, COMPLETED, NOW, end, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_cancelledWithoutAReason_isRefused() {
    BookingTerms terms = terms(NOW, Duration.ofMinutes(60));

    assertThatThrownBy(
            () -> new Booking(ID, terms, RULES, DEPOSIT, CANCELLED, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** BR11: every booking exists only because its deposit was authorized. */
  @Test
  void new_withABlankDepositReference_isRefused() {
    BookingTerms terms = terms(NOW, Duration.ofMinutes(60));

    assertThatThrownBy(() -> Booking.startNow(ID, terms, RULES, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
