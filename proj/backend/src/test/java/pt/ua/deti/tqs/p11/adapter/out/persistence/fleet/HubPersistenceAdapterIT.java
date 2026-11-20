package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.application.fleet.HubCodeAlreadyInUseException;
import pt.ua.deti.tqs.p11.application.fleet.HubRepository;
import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;
import pt.ua.deti.tqs.p11.domain.fleet.GeoPosition;
import pt.ua.deti.tqs.p11.domain.fleet.Hub;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HubPersistenceAdapterIT {

  private static final UUID HUB_03_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000103");
  private static final UUID HUB_05_ID = UUID.fromString("2b3c4d5e-0000-4000-8000-000000000105");
  private static final Hub HUB_03 =
      new Hub(
          HUB_03_ID,
          FleetCode.of("hub-03"),
          "Canal Central",
          new GeoPosition(40.6405, -8.6538),
          10);
  private static final Hub HUB_05 =
      new Hub(
          HUB_05_ID,
          FleetCode.of("hub-05"),
          "Hotel Ria Azul",
          new GeoPosition(40.6421, -8.6524),
          12);

  @Autowired private HubRepository hubs;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate transaction;

  @BeforeEach
  void emptyFleetTables() {
    jdbc.update("DELETE FROM vehicle_state_changes");
    jdbc.update("DELETE FROM vehicles");
    jdbc.update("DELETE FROM hubs");
  }

  @Test
  void save_storesEveryFieldOfTheHub() {
    hubs.save(HUB_03);

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT code, name, latitude, longitude, capacity FROM hubs WHERE id = ?", HUB_03_ID);
    assertThat(row)
        .containsEntry("code", "hub-03")
        .containsEntry("name", "Canal Central")
        .containsEntry("latitude", 40.6405)
        .containsEntry("longitude", -8.6538)
        .containsEntry("capacity", 10);
  }

  @Test
  void findById_returnsTheStoredHub() {
    hubs.save(HUB_03);

    assertThat(hubs.findById(HUB_03_ID)).contains(HUB_03);
  }

  @Test
  void findById_unknownId_isEmpty() {
    assertThat(hubs.findById(HUB_03_ID)).isEmpty();
  }

  @Test
  void save_existingHub_updatesItInPlace() {
    hubs.save(HUB_03);
    Hub lowered = HUB_03.withCapacity(9, 0);

    hubs.save(lowered);

    assertThat(hubs.findById(HUB_03_ID)).contains(lowered);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hubs", Integer.class)).isEqualTo(1);
  }

  @Test
  void save_secondHubWithTheSameCode_isRefused() {
    hubs.save(HUB_03);
    Hub sameCode =
        new Hub(HUB_05_ID, FleetCode.of("hub-03"), "Fórum Aveiro", HUB_05.position(), 5);

    // Bypasses existsByCode, as two concurrent registrations would.
    assertThatThrownBy(() -> hubs.save(sameCode)).isInstanceOf(HubCodeAlreadyInUseException.class);
  }

  @Test
  void existsByCode_isTrueOnlyForStoredCodes() {
    hubs.save(HUB_03);

    assertThat(hubs.existsByCode(FleetCode.of("hub-03"))).isTrue();
    assertThat(hubs.existsByCode(FleetCode.of("hub-05"))).isFalse();
  }

  @Test
  void findAll_returnsEveryHubOrderedByCode() {
    hubs.save(HUB_05);
    hubs.save(HUB_03);

    assertThat(hubs.findAll()).containsExactly(HUB_03, HUB_05);
  }

  /** The lock itself is exercised by the concurrent registration test of the use case (KAN-82). */
  @Test
  void findByIdForUpdate_insideATransaction_returnsTheHub() {
    hubs.save(HUB_03);

    Optional<Hub> locked = transaction.execute(status -> hubs.findByIdForUpdate(HUB_03_ID));

    assertThat(locked).contains(HUB_03);
  }

  /** BR14 also holds for rows edited directly in the database. */
  @Test
  void database_refusesACapacityBelowOne() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO hubs (id, code, name, latitude, longitude, capacity)"
                        + " VALUES (?, 'hub-03', 'Canal Central', 40.6405, -8.6538, 0)",
                    HUB_03_ID))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
