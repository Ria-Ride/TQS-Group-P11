package pt.ua.deti.tqs.p11.domain.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What a telemetry reading changes on a vehicle (US09) and when a reading is too old (BR17). */
class VehicleTelemetryTest {

  private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
  private static final UUID HUB_03 = UUID.fromString("00000000-0000-0000-0000-000000000103");
  private static final FleetCode CODE = FleetCode.of("bike-0009");
  private static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");
  private static final GeoPosition CANAL = new GeoPosition(40.6405, -8.6538);
  private static final GeoPosition RIA = new GeoPosition(40.6421, -8.6524);

  private static Vehicle availableBike() {
    return Vehicle.register(ID, CODE, VehicleType.E_BIKE, HUB_03).activate();
  }

  @Test
  void newVehicle_hasNoReadingYet() {
    Vehicle vehicle = availableBike();

    assertThat(vehicle.battery()).isEmpty();
    assertThat(vehicle.lock()).isEmpty();
    assertThat(vehicle.lastPosition()).isEmpty();
    assertThat(vehicle.lastReading()).isEmpty();
  }

  @Test
  void withReading_setsBatteryLockPositionAndTime_andChangesNothingElse() {
    Vehicle vehicle = availableBike().withReading(BatteryLevel.of(78), LockState.LOCKED, CANAL, T0);

    assertThat(vehicle.battery()).contains(BatteryLevel.of(78));
    assertThat(vehicle.lock()).contains(LockState.LOCKED);
    assertThat(vehicle.lastPosition()).contains(CANAL);
    assertThat(vehicle.lastReading()).contains(T0);
    assertThat(vehicle.state()).isEqualTo(VehicleState.AVAILABLE);
    assertThat(vehicle.currentHub()).contains(HUB_03);
  }

  @Test
  void withReading_newReading_replacesTheOldOne() {
    Vehicle vehicle =
        availableBike()
            .withReading(BatteryLevel.of(78), LockState.LOCKED, CANAL, T0)
            .withReading(BatteryLevel.of(77), LockState.UNLOCKED, RIA, T0.plusSeconds(10));

    assertThat(vehicle.battery()).contains(BatteryLevel.of(77));
    assertThat(vehicle.lock()).contains(LockState.UNLOCKED);
    assertThat(vehicle.lastPosition()).contains(RIA);
    assertThat(vehicle.lastReading()).contains(T0.plusSeconds(10));
  }

  /** A reading that only knows the hub keeps the last known position. */
  @Test
  void withReading_noPosition_keepsTheLastKnownOne() {
    Vehicle vehicle =
        availableBike()
            .withReading(BatteryLevel.of(78), LockState.LOCKED, CANAL, T0)
            .withReading(BatteryLevel.of(77), LockState.LOCKED, null, T0.plusSeconds(10));

    assertThat(vehicle.lastPosition()).contains(CANAL);
    assertThat(vehicle.battery()).contains(BatteryLevel.of(77));
  }

  @Test
  void withReading_firstReadingWithoutPosition_leavesItUnknown() {
    Vehicle vehicle = availableBike().withReading(BatteryLevel.of(78), LockState.LOCKED, null, T0);

    assertThat(vehicle.lastPosition()).isEmpty();
  }

  @Test
  void acceptsReadingAt_noReadingBefore_isTrue() {
    assertThat(availableBike().acceptsReadingAt(T0)).isTrue();
  }

  /** BR17: only a timestamp newer than the last one is accepted; the same instant is not. */
  @Test
  void acceptsReadingAt_isTrueOnlyForATimestampNewerThanTheLastReading() {
    Vehicle vehicle = availableBike().withReading(BatteryLevel.of(78), LockState.LOCKED, CANAL, T0);

    assertThat(vehicle.acceptsReadingAt(T0.minusSeconds(1))).isFalse();
    assertThat(vehicle.acceptsReadingAt(T0)).isFalse();
    assertThat(vehicle.acceptsReadingAt(T0.plusNanos(1))).isTrue();
  }

  @Test
  void withReading_notNewerThanTheLastOne_isRefused() {
    Vehicle vehicle = availableBike().withReading(BatteryLevel.of(78), LockState.LOCKED, CANAL, T0);

    assertThatThrownBy(() -> vehicle.withReading(BatteryLevel.of(50), LockState.LOCKED, CANAL, T0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withReading_missingValues_areRefused() {
    Vehicle vehicle = availableBike();

    assertThatThrownBy(() -> vehicle.withReading(null, LockState.LOCKED, CANAL, T0))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> vehicle.withReading(BatteryLevel.of(1), null, CANAL, T0))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> vehicle.withReading(BatteryLevel.of(1), LockState.LOCKED, CANAL, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void transitions_keepTheLastReading() {
    Vehicle vehicle = availableBike().withReading(BatteryLevel.of(78), LockState.LOCKED, CANAL, T0);

    Vehicle moved = vehicle.startMove();

    assertThat(moved.lock()).contains(LockState.LOCKED);
    assertThat(moved.lastPosition()).contains(CANAL);
    assertThat(moved.lastReading()).contains(T0);
    assertThat(moved.battery()).contains(BatteryLevel.of(78));
  }

  @Test
  void withBattery_keepsTheRestOfTheLastReading() {
    Vehicle vehicle = availableBike().withReading(BatteryLevel.of(78), LockState.LOCKED, CANAL, T0);

    Vehicle changed = vehicle.withBattery(BatteryLevel.of(10));

    assertThat(changed.lock()).contains(LockState.LOCKED);
    assertThat(changed.lastPosition()).contains(CANAL);
    assertThat(changed.lastReading()).contains(T0);
  }
}
