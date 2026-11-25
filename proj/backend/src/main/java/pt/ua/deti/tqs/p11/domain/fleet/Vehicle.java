package pt.ua.deti.tqs.p11.domain.fleet;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * A fleet vehicle and its place in the lifecycle (US08). Immutable: every transition returns a new
 * Vehicle, and the constructor checks the rules, so an invalid Vehicle cannot exist. hubId is the
 * hub the vehicle is at, or null when its state belongs to no hub (InUse, InTransit, Retired).
 * batteryLevel, lockState, position and lastReadingAt are what the last telemetry reading said
 * (US09), and are null before the first report; position is also null while only the hub is known.
 */
public record Vehicle(
    UUID id,
    FleetCode code,
    VehicleType type,
    VehicleState state,
    UUID hubId,
    BatteryLevel batteryLevel,
    LockState lockState,
    GeoPosition position,
    Instant lastReadingAt) {

  public Vehicle {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(state, "state");
    if (state.isAtHub() && hubId == null) {
      throw new IllegalArgumentException("A vehicle in state %s must be at a hub".formatted(state));
    }
    if (!state.isAtHub() && hubId != null) {
      throw new IllegalArgumentException(
          "A vehicle in state %s cannot be at a hub".formatted(state));
    }
  }

  /** A vehicle with no battery reading yet. */
  public Vehicle(UUID id, FleetCode code, VehicleType type, VehicleState state, UUID hubId) {
    this(id, code, type, state, hubId, null, null, null, null);
  }

  /** A vehicle whose only reading is a battery level (tests and the search). */
  public Vehicle(
      UUID id,
      FleetCode code,
      VehicleType type,
      VehicleState state,
      UUID hubId,
      BatteryLevel batteryLevel) {
    this(id, code, type, state, hubId, batteryLevel, null, null, null);
  }

  /** A new vehicle starts Deactivated at the hub where it is registered (BR16). */
  public static Vehicle register(UUID id, FleetCode code, VehicleType type, UUID hubId) {
    return new Vehicle(id, code, type, VehicleState.DEACTIVATED, Objects.requireNonNull(hubId));
  }

  /** The last level reported by telemetry; empty until the first report. */
  public Optional<BatteryLevel> battery() {
    return Optional.ofNullable(batteryLevel);
  }

  /** The lock state of the last reading; empty until the first report. */
  public Optional<LockState> lock() {
    return Optional.ofNullable(lockState);
  }

  /** The last known position; empty until a reading brought one. */
  public Optional<GeoPosition> lastPosition() {
    return Optional.ofNullable(position);
  }

  /** The timestamp of the last reading applied; empty until the first one. */
  public Optional<Instant> lastReading() {
    return Optional.ofNullable(lastReadingAt);
  }

  /** Sets the battery level and changes nothing else. */
  public Vehicle withBattery(BatteryLevel level) {
    return new Vehicle(
        id,
        code,
        type,
        state,
        hubId,
        Objects.requireNonNull(level, "level"),
        lockState,
        position,
        lastReadingAt);
  }

  /** BR17: a reading is accepted only if its timestamp is newer than the last one applied. */
  public boolean acceptsReadingAt(Instant at) {
    Objects.requireNonNull(at, "at");
    return lastReadingAt == null || at.isAfter(lastReadingAt);
  }

  /**
   * Applies a telemetry reading: battery, lock state and time replace the old ones, and so does the
   * position unless the reading has none (only its hub is known), in which case the last known
   * position stays. The lifecycle state and the hub are not changed: they belong to the lifecycle
   * (BR14, BR16), not to a reading.
   *
   * @throws IllegalArgumentException if the timestamp is not newer than the last reading (BR17)
   */
  public Vehicle withReading(
      BatteryLevel level, LockState lock, GeoPosition newPosition, Instant at) {
    Objects.requireNonNull(level, "level");
    Objects.requireNonNull(lock, "lock");
    if (!acceptsReadingAt(at)) {
      throw new IllegalArgumentException(
          "The reading of %s at %s is not newer than the last one".formatted(code, at));
    }
    return new Vehicle(
        id,
        code,
        type,
        state,
        hubId,
        level,
        lock,
        newPosition != null ? newPosition : position,
        at);
  }

  /**
   * BR2 first: a vehicle that is not Available cannot be booked, whatever its battery. Then BR1: an
   * Available vehicle needs a reading at or above the floor, and, when the duration of the ride is
   * given, at or above what that ride needs for this type. A missing reading is refused rather than
   * assumed full, because a flat vehicle must never be offered.
   */
  public Bookability bookability(BatteryRule rule, OptionalInt minutes) {
    Objects.requireNonNull(rule, "rule");
    minutes.ifPresent(BatteryRule::checkMinutes);
    if (state != VehicleState.AVAILABLE) {
      return Bookability.NOT_AVAILABLE;
    }
    if (batteryLevel == null) {
      return Bookability.NO_BATTERY_READING;
    }
    if (!batteryLevel.isAtLeast(rule.floor())) {
      return Bookability.LOW_BATTERY;
    }
    if (minutes.isPresent()
        && batteryLevel.percent() < rule.requiredPercent(type, minutes.getAsInt())) {
      return Bookability.INSUFFICIENT_FOR_DURATION;
    }
    return Bookability.BOOKABLE;
  }

  public Optional<UUID> currentHub() {
    return Optional.ofNullable(hubId);
  }

  public Vehicle activate() {
    return transition(VehicleAction.ACTIVATE, hubId);
  }

  public Vehicle deactivate() {
    return transition(VehicleAction.DEACTIVATE, hubId);
  }

  /** The vehicle leaves its hub while the operator moves it. */
  public Vehicle startMove() {
    return transition(VehicleAction.START_MOVE, hubId);
  }

  /** Whether the destination has a free slot is checked by the caller, which knows the hub. */
  public Vehicle confirmArrival(UUID destinationHubId) {
    Objects.requireNonNull(destinationHubId, "destinationHubId");
    return transition(VehicleAction.CONFIRM_ARRIVAL, destinationHubId);
  }

  /** Retirement is final; the vehicle leaves its hub. */
  public Vehicle retire() {
    return transition(VehicleAction.RETIRE, hubId);
  }

  /**
   * BR18: a fault sends an Available or Reserved vehicle to maintenance, at the same hub. In any
   * other state the vehicle is kept as it is: an InUse ride is not interrupted, and a vehicle that
   * is not offered stays out of service. The fault itself is recorded by its ticket.
   */
  public Vehicle reportFault() {
    throw new UnsupportedOperationException("US10");
  }

  /**
   * Applies the lifecycle table. The vehicle is at hubAfter if the new state is at a hub, and at no
   * hub otherwise.
   */
  private Vehicle transition(VehicleAction action, UUID hubAfter) {
    VehicleState next = state.after(action);
    return new Vehicle(
        id,
        code,
        type,
        next,
        next.isAtHub() ? hubAfter : null,
        batteryLevel,
        lockState,
        position,
        lastReadingAt);
  }
}
