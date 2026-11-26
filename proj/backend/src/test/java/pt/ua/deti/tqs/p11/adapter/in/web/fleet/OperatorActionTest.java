package pt.ua.deti.tqs.p11.adapter.in.web.fleet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleAction;

/**
 * The actions POST /vehicles/{id}/transitions accepts (US08). They are a subset of the lifecycle's
 * actions: starting a use happens only through a booking, after its deposit is held (BR11), so an
 * operator can never put a vehicle InUse.
 */
class OperatorActionTest {

  @ParameterizedTest(name = "{0}")
  @EnumSource(OperatorAction.class)
  void everyOperatorAction_isALifecycleActionOfTheSameName(OperatorAction action) {
    assertThat(VehicleAction.valueOf(action.name())).isNotNull();
  }

  @Test
  void startingAUse_isNotAnOperatorAction() {
    assertThat(OperatorAction.values())
        .extracting(Enum::name)
        .doesNotContain(VehicleAction.START_USE.name());
  }
}