package pt.ua.deti.tqs.p11.domain.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static pt.ua.deti.tqs.p11.domain.fleet.VehicleAction.ACTIVATE;
import static pt.ua.deti.tqs.p11.domain.fleet.VehicleAction.CONFIRM_ARRIVAL;
import static pt.ua.deti.tqs.p11.domain.fleet.VehicleAction.DEACTIVATE;
import static pt.ua.deti.tqs.p11.domain.fleet.VehicleAction.RETIRE;
import static pt.ua.deti.tqs.p11.domain.fleet.VehicleAction.START_MOVE;
import static pt.ua.deti.tqs.p11.domain.fleet.VehicleState.AVAILABLE;
import static pt.ua.deti.tqs.p11.domain.fleet.VehicleState.DEACTIVATED;
import static pt.ua.deti.tqs.p11.domain.fleet.VehicleState.IN_TRANSIT;
import static pt.ua.deti.tqs.p11.domain.fleet.VehicleState.MAINTENANCE;
import static pt.ua.deti.tqs.p11.domain.fleet.VehicleState.RETIRED;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * BR16: one test per allowed transition and one per forbidden (state, action) pair. The allowed
 * transitions are copied from the lifecycle table of the Product Specs (3.3), not from the code;
 * every other pair is generated, so a new state or action is refused everywhere until it is added
 * here.
 */
class VehicleStateTest {

  private static final List<Arguments> ALLOWED =
      List.of(
          arguments(DEACTIVATED, ACTIVATE, AVAILABLE),
          arguments(AVAILABLE, DEACTIVATE, DEACTIVATED),
          arguments(AVAILABLE, START_MOVE, IN_TRANSIT),
          arguments(IN_TRANSIT, CONFIRM_ARRIVAL, AVAILABLE),
          arguments(DEACTIVATED, RETIRE, RETIRED),
          arguments(AVAILABLE, RETIRE, RETIRED),
          arguments(MAINTENANCE, RETIRE, RETIRED));

  static Stream<Arguments> allowedTransitions() {
    return ALLOWED.stream();
  }

  static Stream<Arguments> forbiddenTransitions() {
    return Arrays.stream(VehicleState.values())
        .flatMap(
            state ->
                Arrays.stream(VehicleAction.values())
                    .filter(action -> !isAllowed(state, action))
                    .map(action -> arguments(state, action)));
  }

  private static boolean isAllowed(VehicleState state, VehicleAction action) {
    return ALLOWED.stream()
        .anyMatch(transition -> transition.get()[0] == state && transition.get()[1] == action);
  }

  @ParameterizedTest(name = "{0} --{1}--> {2}")
  @MethodSource("allowedTransitions")
  void after_allowedAction_leadsToTheNextState(
      VehicleState from, VehicleAction action, VehicleState to) {
    assertThat(from.after(action)).isEqualTo(to);
  }

  @ParameterizedTest(name = "{0} --{1}--> refused")
  @MethodSource("forbiddenTransitions")
  void after_forbiddenAction_isRefused(VehicleState from, VehicleAction action) {
    assertThatThrownBy(() -> from.after(action))
        .isInstanceOfSatisfying(
            ForbiddenTransitionException.class,
            e -> {
              assertThat(e.from()).isEqualTo(from);
              assertThat(e.action()).isEqualTo(action);
            });
  }

  /** BR14: InUse, InTransit and Retired vehicles belong to no hub and take no slot. */
  @ParameterizedTest(name = "{0} at a hub: {1}")
  @CsvSource({
    "DEACTIVATED, true",
    "AVAILABLE,   true",
    "RESERVED,    true",
    "MAINTENANCE, true",
    "IN_USE,      false",
    "IN_TRANSIT,  false",
    "RETIRED,     false"
  })
  void isAtHub_onlyForStatesPhysicallyAtAHub(VehicleState state, boolean atHub) {
    assertThat(state.isAtHub()).isEqualTo(atHub);
  }
}
