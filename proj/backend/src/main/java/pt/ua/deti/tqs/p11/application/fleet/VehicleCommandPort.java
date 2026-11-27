package pt.ua.deti.tqs.p11.application.fleet;

import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;

/**
 * Port: commands sent to a vehicle (simulator contract, commands). The core names the vehicle by
 * its code, which is the simulator's vehicle id; the adapter is the only code that knows the wire
 * format (K1).
 */
public interface VehicleCommandPort {

  /**
   * US04: asks the vehicle to unlock, without waiting for its answer. Never throws: the booking is
   * already made, so a command that cannot be sent is only logged.
   */
  void unlock(FleetCode vehicle);

  /**
   * US06: asks the vehicle to lock and waits for its answer, up to a time the adapter sets. Never
   * throws: a command that cannot be sent or gets no answer is {@link LockResult.NoAnswer}.
   */
  LockResult lock(FleetCode vehicle);
}
