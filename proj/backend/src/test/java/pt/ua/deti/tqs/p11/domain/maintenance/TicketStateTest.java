package pt.ua.deti.tqs.p11.domain.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * BR20: an open ticket is one that still needs work. Only one open ticket may exist per vehicle and
 * fault, and a vehicle with an open ticket cannot be activated (BR16).
 */
class TicketStateTest {

  @ParameterizedTest(name = "{0} is open: {1}")
  @CsvSource({
    "OPEN,        true",
    "ASSIGNED,    true",
    "IN_PROGRESS, true",
    "CLOSED,      false",
    "CANCELLED,   false"
  })
  void isOpen_onlyForTicketsThatStillNeedWork(TicketState state, boolean open) {
    assertThat(state.isOpen()).isEqualTo(open);
  }
}
