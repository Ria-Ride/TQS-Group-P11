package pt.ua.deti.tqs.p11.domain.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * How a Vehicle moves through the lifecycle and which hub it is at. Which (state, action) pairs are
 * allowed is tested in VehicleStateTest; here one forbidden case shows the Vehicle refuses too.
 */
class VehicleTest {

  private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
  private static final UUID HUB_03 = UUID.fromString("00000000-0000-0000-0000-000000000103");
  private static final UUID HUB_05 = UUID.fromString("00000000-0000-0000-0000-000000000105");
  private static final FleetCode CODE = FleetCode.of("bike-0009");

  private static Vehicle registeredAtHub03() {
    return Vehicle.register(ID, CODE, VehicleType.E_BIKE, HUB_03);
  }

  @Test
  void register_newVehicle_isDeactivatedAtTheHub() {
    Vehicle vehicle = registeredAtHub03();

    assertThat(vehicle.id()).isEqualTo(ID);
    assertThat(vehicle.code()).isEqualTo(CODE);
    assertThat(vehicle.type()).isEqualTo(VehicleType.E_BIKE);
    assertThat(vehicle.state()).isEqualTo(VehicleState.DEACTIVATED);
    assertThat(vehicle.currentHub()).contains(HUB_03);
  }

  @Test
  void activate_deactivatedVehicle_becomesAvailableAtTheSameHub() {
    Vehicle vehicle = registeredAtHub03().activate();

    assertThat(vehicle.state()).isEqualTo(VehicleState.AVAILABLE);
    assertThat(vehicle.currentHub()).contains(HUB_03);
  }

  @Test
  void deactivate_availableVehicle_becomesDeactivatedAtTheSameHub() {
    Vehicle vehicle = registeredAtHub03().activate().deactivate();

    assertThat(vehicle.state()).isEqualTo(VehicleState.DEACTIVATED);
    assertThat(vehicle.currentHub()).contains(HUB_03);
  }

  @Test
  void startMove_availableVehicle_isInTransitAndLeavesTheHub() {
    Vehicle vehicle = registeredAtHub03().activate().startMove();

    assertThat(vehicle.state()).isEqualTo(VehicleState.IN_TRANSIT);
    assertThat(vehicle.currentHub()).isEmpty();
  }

  @Test
  void startUse_availableVehicle_isInUseAndLeavesTheHub() {
    Vehicle vehicle = registeredAtHub03().activate().withBattery(BatteryLevel.of(80)).startUse();

    assertThat(vehicle.state()).isEqualTo(VehicleState.IN_USE);
    assertThat(vehicle.currentHub()).isEmpty();
    assertThat(vehicle.battery()).contains(BatteryLevel.of(80));
  }

  @Test
  void confirmArrival_vehicleInTransit_becomesAvailableAtTheDestination() {
    Vehicle vehicle = registeredAtHub03().activate().startMove().confirmArrival(HUB_05);

    assertThat(vehicle.state()).isEqualTo(VehicleState.AVAILABLE);
    assertThat(vehicle.currentHub()).contains(HUB_05);
  }

  @Test
  void confirmArrival_withoutDestination_isRejected() {
    Vehicle inTransit = registeredAtHub03().activate().startMove();

    assertThatThrownBy(() -> inTransit.confirmArrival(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void retire_availableVehicle_isRetiredAndLeavesTheHub() {
    Vehicle vehicle = registeredAtHub03().activate().retire();

    assertThat(vehicle.state()).isEqualTo(VehicleState.RETIRED);
    assertThat(vehicle.currentHub()).isEmpty();
  }

  @Test
  void activate_retiredVehicle_isRefused() {
    Vehicle retired = registeredAtHub03().retire();

    assertThatThrownBy(retired::activate).isInstanceOf(ForbiddenTransitionException.class);
  }

  @ParameterizedTest
  @EnumSource(names = {"DEACTIVATED", "AVAILABLE", "RESERVED", "MAINTENANCE"})
  void new_stateAtAHubWithoutAHub_isRejected(VehicleState state) {
    assertThatThrownBy(() -> new Vehicle(ID, CODE, VehicleType.E_BIKE, state, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @EnumSource(names = {"IN_USE", "IN_TRANSIT", "RETIRED"})
  void new_stateOutsideAHubWithAHub_isRejected(VehicleState state) {
    assertThatThrownBy(() -> new Vehicle(ID, CODE, VehicleType.E_BIKE, state, HUB_03))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void new_missingField_isRejected() {
    VehicleType bike = VehicleType.E_BIKE;
    VehicleState deactivated = VehicleState.DEACTIVATED;

    assertThatThrownBy(() -> new Vehicle(null, CODE, bike, deactivated, HUB_03))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Vehicle(ID, null, bike, deactivated, HUB_03))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Vehicle(ID, CODE, null, deactivated, HUB_03))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Vehicle(ID, CODE, bike, null, HUB_03))
        .isInstanceOf(NullPointerException.class);
  }
}
