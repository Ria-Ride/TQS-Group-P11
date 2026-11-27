package pt.ua.deti.tqs.p11.application.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.application.DirectTransactionRunner;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.LockState;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;

/**
 * Applying a telemetry reading to a vehicle (US09, BR17). A reading is applied only if the battery
 * is from 0 to 100, the vehicle is known and the timestamp is newer than the last one; otherwise it
 * is discarded and nothing is saved. The vehicle is found "for update", so a reading cannot race a
 * booking or another reading of the same vehicle (K3): with a plain find the stubs would not
 * answer.
 */
@ExtendWith(MockitoExtension.class)
class TelemetryIngestionUseCaseTest {

  private static final UUID BIKE_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000009");
  private static final UUID HUB_03_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000103");
  private static final FleetCode BIKE_CODE = FleetCode.of("bike-0009");
  private static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");
  private static final GeoPosition CANAL = new GeoPosition(40.6405, -8.6538);
  private static final FaultCode BRAKE_WEAR = FaultCode.of("BRAKE_WEAR");

  @Mock private VehicleRepository vehicles;
  @Mock private FaultReportListener faults;

  private TelemetryIngestionUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new TelemetryIngestionUseCase(vehicles, new DirectTransactionRunner(), faults);
  }

  private static Vehicle bike() {
    return Vehicle.register(BIKE_ID, BIKE_CODE, VehicleType.E_BIKE, HUB_03_ID).activate();
  }

  private static VehicleReading reading(double battery, Instant at) {
    return new VehicleReading(BIKE_CODE, battery, LockState.LOCKED, CANAL, at);
  }

  private void bikeIs(Vehicle vehicle) {
    when(vehicles.findByCodeForUpdate(BIKE_CODE)).thenReturn(Optional.of(vehicle));
  }

  private Vehicle savedVehicle() {
    ArgumentCaptor<Vehicle> saved = forClass(Vehicle.class);
    verify(vehicles).save(saved.capture());
    return saved.getValue();
  }

  // ------------------------------------------------------------- a valid reading

  @Test
  void report_validReading_isAppliedToBatteryLockPositionAndTime() {
    bikeIs(bike());

    ReadingOutcome outcome = useCase.report(reading(78.0, T0));

    assertThat(outcome).isEqualTo(ReadingOutcome.APPLIED);
    Vehicle saved = savedVehicle();
    assertThat(saved.battery()).contains(BatteryLevel.of(78));
    assertThat(saved.lock()).contains(LockState.LOCKED);
    assertThat(saved.lastPosition()).contains(CANAL);
    assertThat(saved.lastReading()).contains(T0);
  }

  /** A decimal battery is rounded down, so the platform never shows more charge than there is. */
  @Test
  void report_decimalBattery_isRoundedDown() {
    bikeIs(bike());

    useCase.report(reading(78.9, T0));

    assertThat(savedVehicle().battery()).contains(BatteryLevel.of(78));
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 0.4, 100.0})
  void report_batteryAtTheLimits_isAccepted(double battery) {
    bikeIs(bike());

    assertThat(useCase.report(reading(battery, T0))).isEqualTo(ReadingOutcome.APPLIED);
  }

  @Test
  void report_readingWithoutPosition_keepsTheLastKnownPosition() {
    bikeIs(bike().withReading(BatteryLevel.of(80), LockState.LOCKED, CANAL, T0));

    useCase.report(new VehicleReading(BIKE_CODE, 79, LockState.UNLOCKED, null, T0.plusSeconds(10)));

    Vehicle saved = savedVehicle();
    assertThat(saved.lastPosition()).contains(CANAL);
    assertThat(saved.lock()).contains(LockState.UNLOCKED);
  }

  @Test
  void report_changesNeitherTheStateNorTheHub() {
    bikeIs(bike());

    useCase.report(reading(78, T0));

    assertThat(savedVehicle().state()).isEqualTo(bike().state());
    assertThat(savedVehicle().currentHub()).contains(HUB_03_ID);
  }

  // ------------------------------------------------------------- BR17: discarded

  /** BR17: -1 and 101 are discarded, and so is a value that is not a number. */
  @ParameterizedTest
  @ValueSource(doubles = {-1.0, -0.1, 100.1, 101.0, Double.NaN, Double.POSITIVE_INFINITY})
  void report_batteryOutsideZeroToHundred_isDiscardedWithoutReadingTheVehicle(double battery) {
    ReadingOutcome outcome = useCase.report(reading(battery, T0));

    assertThat(outcome).isEqualTo(ReadingOutcome.INVALID_BATTERY);
    verify(vehicles, never()).findByCodeForUpdate(any());
    verify(vehicles, never()).save(any());
  }

  @Test
  void report_unknownVehicle_isDiscarded() {
    when(vehicles.findByCodeForUpdate(BIKE_CODE)).thenReturn(Optional.empty());

    ReadingOutcome outcome = useCase.report(reading(78, T0));

    assertThat(outcome).isEqualTo(ReadingOutcome.UNKNOWN_VEHICLE);
    verify(vehicles, never()).save(any());
  }

  @Test
  void report_timestampEqualToTheLastOne_isDiscarded() {
    bikeIs(bike().withReading(BatteryLevel.of(80), LockState.LOCKED, CANAL, T0));

    ReadingOutcome outcome = useCase.report(reading(50, T0));

    assertThat(outcome).isEqualTo(ReadingOutcome.STALE);
    verify(vehicles, never()).save(any());
  }

  @Test
  void report_olderTimestamp_isDiscarded() {
    bikeIs(bike().withReading(BatteryLevel.of(80), LockState.LOCKED, CANAL, T0));

    ReadingOutcome outcome = useCase.report(reading(50, T0.minusSeconds(1)));

    assertThat(outcome).isEqualTo(ReadingOutcome.STALE);
    verify(vehicles, never()).save(any());
  }

  @Test
  void report_timestampJustNewer_isApplied() {
    bikeIs(bike().withReading(BatteryLevel.of(80), LockState.LOCKED, CANAL, T0));

    assertThat(useCase.report(reading(50, T0.plusMillis(1)))).isEqualTo(ReadingOutcome.APPLIED);
  }

  /** Both problems at once: the battery is judged first, before any lookup. */
  @Test
  void report_badBatteryAndUnknownVehicle_reportsTheBattery() {
    assertThat(useCase.report(reading(101, T0))).isEqualTo(ReadingOutcome.INVALID_BATTERY);
  }

  // ------------------------------------------------------------- faults (US10)

  private static VehicleReading readingWithFault(double battery, Instant at) {
    return new VehicleReading(BIKE_CODE, battery, LockState.LOCKED, CANAL, at, BRAKE_WEAR);
  }

  /** BR18: maintenance sees the vehicle as saved, with the reading already applied. */
  @Test
  void report_appliedReadingWithAFault_isPassedOnWithTheUpdatedVehicle() {
    bikeIs(bike());

    ReadingOutcome outcome = useCase.report(readingWithFault(78.0, T0));

    assertThat(outcome).isEqualTo(ReadingOutcome.APPLIED);
    ArgumentCaptor<Vehicle> reported = forClass(Vehicle.class);
    verify(faults).faultReported(reported.capture(), eq(BRAKE_WEAR));
    assertThat(reported.getValue()).isEqualTo(savedVehicle());
  }

  @Test
  void report_readingWithoutAFault_reportsNoFault() {
    bikeIs(bike());

    useCase.report(reading(78.0, T0));

    verify(faults, never()).faultReported(any(), any());
  }

  /** BR17: a discarded reading changes nothing, so its fault is not reported either. */
  @Test
  void report_staleReadingWithAFault_reportsNoFault() {
    bikeIs(bike().withReading(BatteryLevel.of(80), LockState.LOCKED, CANAL, T0));

    ReadingOutcome outcome = useCase.report(readingWithFault(78.0, T0));

    assertThat(outcome).isEqualTo(ReadingOutcome.STALE);
    verify(faults, never()).faultReported(any(), any());
  }

  @Test
  void report_invalidBatteryWithAFault_reportsNoFault() {
    ReadingOutcome outcome = useCase.report(readingWithFault(101, T0));

    assertThat(outcome).isEqualTo(ReadingOutcome.INVALID_BATTERY);
    verify(faults, never()).faultReported(any(), any());
  }
}
