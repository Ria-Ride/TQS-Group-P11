package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.application.pass.PassTypeRepository;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.pass.PassType;

/**
 * Pass types made and changed by the client manager (US14), against a real PostgreSQL. A test
 * removes the pass types it made (their code starts with IT_), so the seeded two stay the only ones
 * for the rest of the suite.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PassTypeManagementPersistenceIT {

  private static final UUID WEEK_ID = UUID.fromString("7a1e0000-0000-4000-8000-0000000000b1");

  @Autowired private PassTypeRepository passTypes;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void removeThePassTypesOfThisTest() {
    jdbc.update(
        "DELETE FROM pass_type_vehicle_types WHERE pass_type_id IN"
            + " (SELECT id FROM pass_types WHERE code LIKE 'IT\\_%')");
    jdbc.update("DELETE FROM pass_types WHERE code LIKE 'IT\\_%'");
  }

  private static PassType week() {
    return new PassType(
        WEEK_ID, "IT_WEEK", "Week pass", 7, Money.of("40"), Set.of(VehicleType.E_BIKE));
  }

  @Test
  void save_newPassType_isStoredWithEveryFieldAndItsVehicleTypes() {
    PassType saved = passTypes.save(week());

    assertThat(saved).isEqualTo(week());
    assertThat(passTypes.findById(WEEK_ID)).contains(week());
    assertThat(passTypes.findAll()).extracting(PassType::code).contains("IT_WEEK");
  }

  @Test
  void save_existingPassType_isUpdatedInPlaceWithItsNewVehicleTypes() {
    passTypes.save(week());
    PassType changed =
        new PassType(
            WEEK_ID,
            "IT_WEEK",
            "Week pass plus",
            8,
            Money.of("45.50"),
            Set.of(VehicleType.E_BIKE, VehicleType.E_SCOOTER));

    passTypes.save(changed);

    assertThat(passTypes.findById(WEEK_ID)).contains(changed);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pass_types WHERE code = 'IT_WEEK'", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pass_type_vehicle_types WHERE pass_type_id = ?",
                Integer.class,
                WEEK_ID))
        .isEqualTo(2);
  }

  @Test
  void save_vehicleTypeRemoved_isRemovedFromTheStore() {
    passTypes.save(
        new PassType(
            WEEK_ID,
            "IT_WEEK",
            "Week pass",
            7,
            Money.of("40"),
            Set.of(VehicleType.E_BIKE, VehicleType.E_SCOOTER)));

    passTypes.save(week());

    assertThat(passTypes.findById(WEEK_ID).orElseThrow().vehicleTypes())
        .containsExactly(VehicleType.E_BIKE);
  }

  @Test
  void existsByCode_isTrueOnlyForStoredCodes() {
    passTypes.save(week());

    assertThat(passTypes.existsByCode("IT_WEEK")).isTrue();
    assertThat(passTypes.existsByCode("DAY")).isTrue();
    assertThat(passTypes.existsByCode("IT_NOPE")).isFalse();
  }

  @Test
  void database_refusesASecondPassTypeWithTheSameCode() {
    passTypes.save(week());

    assertThatThrownBy(() ->
            passTypes.save(
                new PassType(
                    UUID.randomUUID(),
                    "IT_WEEK",
                    "Copy",
                    1,
                    Money.of("1"),
                    Set.of(VehicleType.E_BIKE))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
