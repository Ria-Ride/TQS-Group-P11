package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.application.fleet.HubRepository;
import pt.ua.deti.tqs.p11.application.fleet.VehicleCodeAlreadyInUseException;
import pt.ua.deti.tqs.p11.application.fleet.VehicleRepository;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;
import pt.ua.deti.tqs.p11.domain.fleet.Vehicle;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VehiclePersistenceAdapterIT {

  private static final UUID HUB_03_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000103");
  private static final UUID HUB_05_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000105");
  private static final UUID BIKE_09_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000009");
  private static final UUID BIKE_10_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000010");
  private static final UUID SCOOTER_07_ID =
      UUID.fromString("3c4d5e6f-0000-4000-8000-000000000107");

  @Autowired private VehicleRepository vehicles;
  @Autowired private HubRepository hubs;
  @Autowired private JdbcTemplate jdbc;

  private static Vehicle registered(UUID id, String code, VehicleType type, UUID hubId) {
    return Vehicle.register(id, FleetCode.of(code), type, hubId);
  }

  private static Vehicle bike09() {
    return registered(BIKE_09_ID, "bike-0009", VehicleType.E_BIKE, HUB_03_ID);
  }

  @BeforeEach
  void startWithTwoEmptyHubs() {
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
    hubs.save(
        new Hub(
            HUB_05_ID,
            FleetCode.of("hub-05"),
            "Hotel Ria Azul",
            new GeoPosition(40.6421, -8.6524),
            12));
  }

  @Test
  void save_storesEveryFieldOfTheVehicle() {
    vehicles.save(bike09());

    Map<String, Object> row =
        jdbc.queryForMap("SELECT code, type, state, hub_id FROM vehicles WHERE id = ?", BIKE_09_ID);
    assertThat(row)
        .containsEntry("code", "bike-0009")
        .containsEntry("type", "E_BIKE")
        .containsEntry("state", "DEACTIVATED")
        .containsEntry("hub_id", HUB_03_ID);
  }

  @Test
  void findById_returnsTheStoredVehicle() {
    vehicles.save(bike09());

    assertThat(vehicles.findById(BIKE_09_ID)).contains(bike09());
  }

  @Test
  void findById_unknownId_isEmpty() {
    assertThat(vehicles.findById(BIKE_09_ID)).isEmpty();
  }

  @Test
  void save_vehicleThatLeftItsHub_updatesItInPlaceWithNoHub() {
    vehicles.save(bike09());
    Vehicle inTransit = bike09().activate().startMove();

    vehicles.save(inTransit);

    assertThat(vehicles.findById(BIKE_09_ID)).contains(inTransit);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vehicles", Integer.class)).isEqualTo(1);
  }

  @Test
  void save_secondVehicleWithTheSameCode_isRefused() {
    vehicles.save(bike09());
    Vehicle sameCode = registered(BIKE_10_ID, "bike-0009", VehicleType.E_SCOOTER, HUB_05_ID);

    // Bypasses existsByCode, as two concurrent registrations would.
    assertThatThrownBy(() -> vehicles.save(sameCode))
        .isInstanceOf(VehicleCodeAlreadyInUseException.class);
  }

  @Test
  void existsByCode_isTrueOnlyForStoredCodes() {
    vehicles.save(bike09());

    assertThat(vehicles.existsByCode(FleetCode.of("bike-0009"))).isTrue();
    assertThat(vehicles.existsByCode(FleetCode.of("bike-0010"))).isFalse();
  }

  /** BR14: a vehicle in transit belongs to no hub, so it is not counted. */
  @Test
  void countAtHub_countsOnlyTheVehiclesAtThatHub() {
    vehicles.save(bike09());
    vehicles.save(registered(BIKE_10_ID, "bike-0010", VehicleType.E_BIKE, HUB_03_ID).activate());
    vehicles.save(
        registered(SCOOTER_07_ID, "scooter-0007", VehicleType.E_SCOOTER, HUB_03_ID)
            .activate()
            .startMove());
    vehicles.save(
        registered(
            UUID.fromString("3c4d5e6f-0000-4000-8000-000000000011"),
            "bike-0011",
            VehicleType.E_BIKE,
            HUB_05_ID));

    assertThat(vehicles.countAtHub(HUB_03_ID)).isEqualTo(2);
    assertThat(vehicles.countAtHub(HUB_05_ID)).isEqualTo(1);
  }

  @Test
  void countAtHub_emptyHub_isZero() {
    assertThat(vehicles.countAtHub(HUB_03_ID)).isZero();
  }

  @Test
  void findAll_returnsEveryVehicleOrderedByCode() {
    Vehicle scooter = registered(SCOOTER_07_ID, "scooter-0007", VehicleType.E_SCOOTER, HUB_05_ID);
    vehicles.save(scooter);
    vehicles.save(bike09());

    assertThat(vehicles.findAll()).containsExactly(bike09(), scooter);
  }

  /** A vehicle is at a hub if and only if its state is at a hub, also for rows edited by hand. */
  @Test
  void database_refusesAVehicleInTransitWithAHub() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO vehicles (id, code, type, state, hub_id)"
                        + " VALUES (?, 'bike-0009', 'E_BIKE', 'IN_TRANSIT', ?)",
                    BIKE_09_ID,
                    HUB_03_ID))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
