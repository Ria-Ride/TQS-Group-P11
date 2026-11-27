package pt.ua.deti.tqs.p11.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static pt.ua.deti.tqs.p11.domain.booking.BookingAction.CANCEL;
import static pt.ua.deti.tqs.p11.domain.booking.BookingAction.COMPLETE;
import static pt.ua.deti.tqs.p11.domain.booking.BookingAction.EXPIRE;
import static pt.ua.deti.tqs.p11.domain.booking.BookingAction.START;
import static pt.ua.deti.tqs.p11.domain.booking.BookingState.ACTIVE;
import static pt.ua.deti.tqs.p11.domain.booking.BookingState.CANCELLED;
import static pt.ua.deti.tqs.p11.domain.booking.BookingState.COMPLETED;
import static pt.ua.deti.tqs.p11.domain.booking.BookingState.EXPIRED;
import static pt.ua.deti.tqs.p11.domain.booking.BookingState.RESERVED;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * One test per allowed transition and one per forbidden (state, action) pair. The allowed
 * transitions are copied from the booking lifecycle of the Product Specs (3.3), not from the code;
 * every other pair is generated, so a new state or action is refused everywhere until it is added
 * here.
 */
class BookingStateTest {

  private static final List<Arguments> ALLOWED =
      List.of(
          arguments(RESERVED, START, ACTIVE),
          arguments(RESERVED, CANCEL, CANCELLED),
          arguments(RESERVED, EXPIRE, EXPIRED),
          arguments(ACTIVE, COMPLETE, COMPLETED));

  static Stream<Arguments> allowedTransitions() {
    return ALLOWED.stream();
  }

  static Stream<Arguments> forbiddenTransitions() {
    return Arrays.stream(BookingState.values())
        .flatMap(state ->
            Arrays.stream(BookingAction.values())
                .filter(action -> !isAllowed(state, action))
                .map(action -> arguments(state, action)));
  }

  private static boolean isAllowed(BookingState state, BookingAction action) {
    return ALLOWED.stream()
        .anyMatch(transition -> transition.get()[0] == state && transition.get()[1] == action);
  }

  @ParameterizedTest(name = "{0} --{1}--> {2}")
  @MethodSource("allowedTransitions")
  void after_allowedAction_leadsToTheNextState(
      BookingState from, BookingAction action, BookingState to) {
    assertThat(from.after(action)).isEqualTo(to);
  }

  @ParameterizedTest(name = "{0} --{1}--> refused")
  @MethodSource("forbiddenTransitions")
  void after_forbiddenAction_isRefused(BookingState from, BookingAction action) {
    assertThatThrownBy(() -> from.after(action))
        .isInstanceOfSatisfying(
            BookingTransitionNotAllowedException.class,
            e -> {
              assertThat(e.from()).isEqualTo(from);
              assertThat(e.action()).isEqualTo(action);
            });
  }

  /** BR3, BR6: only a Reserved or Active booking holds its vehicle and its period. */
  @ParameterizedTest(name = "{0} ongoing: {1}")
  @CsvSource({
    "RESERVED,  true",
    "ACTIVE,    true",
    "COMPLETED, false",
    "CANCELLED, false",
    "EXPIRED,   false"
  })
  void isOngoing_onlyWhileTheBookingHoldsTheVehicle(BookingState state, boolean ongoing) {
    assertThat(state.isOngoing()).isEqualTo(ongoing);
  }
}
