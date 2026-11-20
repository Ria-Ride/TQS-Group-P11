package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.application.fleet.HubRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleStateChangeRepository;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleState;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleStateChange;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VehicleStateChangePersistenceAdapterIT {

  private static final UUID HUB_03_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000103");
  private static final UUID BIKE_09_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000009");
  private static final UUID BIKE_10_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000010");
  private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");

  @Autowired private VehicleStateChangeRepository history;
  @Autowired private VehicleRepository vehicles;
  @Autowired private HubRepository hubs;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void startWithTwoRegisteredBikes() {
    jdbc.update("DELETE FROM vehicle_state_changes");
    jdbc.update("DELETE FROM vehicles");
    jdbc.update("DELETE FROM hubs");
    hubs.save(
        new Hub(
            HUB_03_ID,
            FleetCode.of("hub-03"),
            "Canal Central",
            new GeoPosition(40.6405, -8.6538),
            10));
    vehicles.save(
        Vehicle.register(BIKE_09_ID, FleetCode.of("bike-0009"), VehicleType.E_BIKE, HUB_03_ID));
    vehicles.save(
        Vehicle.register(BIKE_10_ID, FleetCode.of("bike-0010"), VehicleType.E_BIKE, HUB_03_ID));
  }

  @Test
  void findByVehicleId_returnsTheChangesInTheOrderTheyHappened() {
    VehicleStateChange registration = VehicleStateChange.registration(BIKE_09_ID, T0);
    VehicleStateChange activation =
        new VehicleStateChange(
            BIKE_09_ID, VehicleState.DEACTIVATED, VehicleState.AVAILABLE, T0.plusSeconds(60));
    VehicleStateChange move =
        new VehicleStateChange(
            BIKE_09_ID, VehicleState.AVAILABLE, VehicleState.IN_TRANSIT, T0.plusSeconds(120));
    history.save(registration);
    history.save(activation);
    history.save(move);

    assertThat(history.findByVehicleId(BIKE_09_ID)).containsExactly(registration, activation, move);
  }

  /** With a fixed clock in tests, several changes can share the same instant. */
  @Test
  void findByVehicleId_changesAtTheSameInstant_keepTheOrderTheyWereSaved() {
    VehicleStateChange registration = VehicleStateChange.registration(BIKE_09_ID, T0);
    VehicleStateChange activation =
        new VehicleStateChange(BIKE_09_ID, VehicleState.DEACTIVATED, VehicleState.AVAILABLE, T0);
    history.save(registration);
    history.save(activation);

    assertThat(history.findByVehicleId(BIKE_09_ID)).containsExactly(registration, activation);
  }

  @Test
  void findByVehicleId_returnsOnlyThatVehicle() {
    history.save(VehicleStateChange.registration(BIKE_09_ID, T0));
    VehicleStateChange otherBike = VehicleStateChange.registration(BIKE_10_ID, T0);
    history.save(otherBike);

    assertThat(history.findByVehicleId(BIKE_10_ID)).containsExactly(otherBike);
  }

  @Test
  void findByVehicleId_vehicleWithNoHistory_isEmpty() {
    assertThat(history.findByVehicleId(BIKE_09_ID)).isEmpty();
  }
}
