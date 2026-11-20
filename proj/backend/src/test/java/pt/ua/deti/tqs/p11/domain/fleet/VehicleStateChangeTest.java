package pt.ua.deti.tqs.p11.domain.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VehicleStateChangeTest {

  private static final UUID VEHICLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
  private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
  private static final Instant AT = Instant.parse("2026-11-20T10:00:00Z");

  private static Vehicle registered(UUID id, String code) {
    return Vehicle.register(id, FleetCode.of(code), VehicleType.E_BIKE, HUB_ID);
  }

  @Test
  void registration_hasNoPreviousStateAndEndsDeactivated() {
    VehicleStateChange change = VehicleStateChange.registration(VEHICLE_ID, AT);

    assertThat(change)
        .isEqualTo(new VehicleStateChange(VEHICLE_ID, null, VehicleState.DEACTIVATED, AT));
  }

  @Test
  void between_takesTheStatesOfBothVersionsOfTheVehicle() {
    Vehicle before = registered(VEHICLE_ID, "bike-0009");
    Vehicle after = before.activate();

    assertThat(VehicleStateChange.between(before, after, AT))
        .isEqualTo(
            new VehicleStateChange(
                VEHICLE_ID, VehicleState.DEACTIVATED, VehicleState.AVAILABLE, AT));
  }

  @Test
  void between_twoDifferentVehicles_isRejected() {
    Vehicle before = registered(VEHICLE_ID, "bike-0009");
    Vehicle other = registered(OTHER_ID, "bike-0010").activate();

    assertThatThrownBy(() -> VehicleStateChange.between(before, other, AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_sameStateOnBothSides_isRejected() {
    assertThatThrownBy(() ->
            new VehicleStateChange(VEHICLE_ID, VehicleState.AVAILABLE, VehicleState.AVAILABLE, AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_missingField_isRejected() {
    VehicleState available = VehicleState.AVAILABLE;

    assertThatThrownBy(() -> new VehicleStateChange(null, null, available, AT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new VehicleStateChange(VEHICLE_ID, null, null, AT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new VehicleStateChange(VEHICLE_ID, null, available, null))
        .isInstanceOf(NullPointerException.class);
  }
}
