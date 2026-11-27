package pt.ua.deti.tqs.p11.application.telemetry;

import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;

/**
 * Applies telemetry readings to the vehicles (US09). BR17: a reading is applied only if its battery
 * is from 0 to 100, its vehicle is known and its timestamp is newer than the last one applied; any
 * other reading is discarded and logged, and changes nothing. The vehicle is locked while it is
 * changed, so a reading cannot race a booking or another reading of the same vehicle (K3). Hub,
 * lifecycle state are not touched here: they belong to the lifecycle (BR14, BR16). A reading that
 * is applied and reports a fault is passed on to the FaultReportListener (BR18, US10), in the same
 * transaction.
 */
public class TelemetryIngestionUseCase implements TelemetryIngestionPort {

  private static final Logger LOG = LoggerFactory.getLogger(TelemetryIngestionUseCase.class);

  private static final double MIN_BATTERY = 0;
  private static final double MAX_BATTERY = 100;

  private final VehicleRepository vehicles;
  private final TransactionRunner transactions;
  private final FaultReportListener faults;

  public TelemetryIngestionUseCase(
      VehicleRepository vehicles, TransactionRunner transactions, FaultReportListener faults) {
    this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.faults = Objects.requireNonNull(faults, "faults");
  }

  @Override
  public ReadingOutcome report(VehicleReading reading) {
    if (!(reading.batteryPercent() >= MIN_BATTERY && reading.batteryPercent() <= MAX_BATTERY)) {
      return discarded(reading, ReadingOutcome.INVALID_BATTERY);
    }
    // Rounded down, so the platform never shows more charge than the vehicle has.
    BatteryLevel battery = BatteryLevel.of((int) Math.floor(reading.batteryPercent()));
    return transactions.inTransaction(() -> apply(reading, battery));
  }

  private ReadingOutcome apply(VehicleReading reading, BatteryLevel battery) {
    Optional<Vehicle> found = vehicles.findByCodeForUpdate(reading.vehicleCode());
    if (found.isEmpty()) {
      return discarded(reading, ReadingOutcome.UNKNOWN_VEHICLE);
    }
    Vehicle vehicle = found.get();
    if (!vehicle.acceptsReadingAt(reading.timestamp())) {
      return discarded(reading, ReadingOutcome.STALE);
    }
    Vehicle updated =
        vehicle.withReading(battery, reading.lockState(), reading.position(), reading.timestamp());
    vehicles.save(updated);
    reading.fault().ifPresent(fault -> faults.faultReported(updated, fault));
    return ReadingOutcome.APPLIED;
  }

  private static ReadingOutcome discarded(VehicleReading reading, ReadingOutcome reason) {
    LOG.warn(
        "Telemetry reading discarded ({}): vehicle {}, battery {}, timestamp {}",
        reason,
        reading.vehicleCode(),
        reading.batteryPercent(),
        reading.timestamp());
    return reason;
  }
}
