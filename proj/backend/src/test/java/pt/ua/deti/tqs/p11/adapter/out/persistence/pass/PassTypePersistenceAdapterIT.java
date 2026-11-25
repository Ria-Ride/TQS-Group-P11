package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.application.pass.PassTypeRepository;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.pass.PassType;

/**
 * The seeded pass types and partner hotel (KAN-98), read through the port. The database starts
 * empty and Flyway runs every migration, so this also checks that the seed runs from scratch.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PassTypePersistenceAdapterIT {

  private static final UUID DAY_ID = UUID.fromString("7a1e0000-0000-4000-8000-000000000001");
  private static final Set<VehicleType> BOTH = Set.of(VehicleType.E_BIKE, VehicleType.E_SCOOTER);

  @Autowired private PassTypeRepository passTypes;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void findAll_returnsTheSeededTypesShortestFirst() {
    assertThat(passTypes.findAll())
        .extracting(PassType::code, PassType::validityDays, PassType::basePrice)
        .containsExactly(
            tuple("DAY", 1, Money.of("10.00")), tuple("MULTI_DAY", 3, Money.of("25.00")));
  }

  @Test
  void findById_returnsEveryFieldIncludingTheVehicleTypes() {
    assertThat(passTypes.findById(DAY_ID))
        .contains(new PassType(DAY_ID, "DAY", "Day pass", 1, Money.of("10.00"), BOTH));
  }

  @Test
  void findById_unknownId_isEmpty() {
    assertThat(passTypes.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void seed_hasThePartnerHotelWithTenPercentOff() {
    assertThat(
            jdbc.queryForObject(
                "SELECT discount_percent FROM partner_accommodations WHERE name = ?",
                Integer.class,
                "Hotel Ria Azul"))
        .isEqualTo(10);
  }

  /** Demo codes: valid during the project, one over and one not started, all upper-case. */
  @Test
  void seed_hasDemoStayCodes() {
    assertThat(jdbc.queryForList("SELECT code FROM stays ORDER BY code", String.class))
        .contains("RIA-DEMO-01", "RIA-PAST-01", "RIA-SOON-01");
  }
}
