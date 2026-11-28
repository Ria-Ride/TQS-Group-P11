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
   * US04, US05: the vehicle is handed to a tourist and leaves its hub, from Available (immediate
   * use) or Reserved (a reservation started). Whether it may be is checked by the caller on the
   * locked vehicle.
   */
  public Vehicle startUse() {
    return transition(VehicleAction.START_USE, hubId);
  }

  /**
   * US05: the vehicle is held at its hub for a booking that starts later. Whether it may be
   * (battery, the booking rules) is checked by the caller on the locked vehicle.
   */
  public Vehicle reserve() {
    return transition(VehicleAction.RESERVE, hubId);
  }

  /** US05: a reservation was cancelled or expired, so the vehicle is offered again at its hub. */
  public Vehicle release() {
    return transition(VehicleAction.RELEASE, hubId);
  }

  /**
   * US06: the ride ends and the vehicle is offered again at the hub where it was left, which may
   * not be the one it started from. Whether that hub has a free slot (BR14) is checked by the
   * caller, which knows the hub.
   */
  public Vehicle endUse(UUID hubId) {
    Objects.requireNonNull(hubId, "hubId");
    return transition(VehicleAction.END_USE, hubId);
  }

  /**
   * BR18: the ride ends while a fault ticket of the vehicle is open, so it is left at the hub in
   * Maintenance instead of being offered again. Whether a ticket is open, and whether the hub has a
   * free slot (BR14), are checked by the caller.
   */
  public Vehicle endUseForRepair(UUID hubId) {
    throw new UnsupportedOperationException("KAN-733");
  }

  /**
   * BR18: a fault sends an Available or Reserved vehicle to maintenance, at the same hub. In any
   * other state the vehicle is kept as it is: an InUse ride is not interrupted, and a vehicle that
   * is not offered stays out of service. The fault itself is recorded by its ticket.
   */
  public Vehicle reportFault() {
    return state.allows(VehicleAction.REPORT_FAULT)
        ? transition(VehicleAction.REPORT_FAULT, hubId)
        : this;
  }

  /**
   * BR20: a repaired vehicle goes back to Available at the same hub. Whether another ticket is
   * still open for it is checked by the caller on the locked vehicle.
   */
  public Vehicle returnToService() {
    return transition(VehicleAction.RETURN_TO_SERVICE, hubId);
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
