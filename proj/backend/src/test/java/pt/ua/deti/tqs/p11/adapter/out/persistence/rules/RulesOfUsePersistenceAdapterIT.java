package pt.ua.deti.tqs.p11.adapter.out.persistence.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.application.rules.RulesOfUseRepository;
import pt.ua.deti.tqs.p11.application.rules.RulesVersionConflictException;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.fleet.BatteryLevel;
import pt.ua.deti.tqs.p11.domain.fleet.VehicleType;
import pt.ua.deti.tqs.p11.domain.rules.RulesChange;
import pt.ua.deti.tqs.p11.domain.rules.RulesOfUse;

/**
 * The versions of the rules of use (US14, BR21) against a real PostgreSQL. The database starts with
 * version 1, made by the migration; a test adds versions and removes them again, so the rest of the
 * suite always starts from version 1.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RulesOfUsePersistenceAdapterIT {

  private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000066");

  @Autowired private RulesOfUseRepository rules;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void keepOnlyTheSeededVersion() {
    jdbc.update("DELETE FROM rules_of_use_vehicle_types WHERE version > 1");
    jdbc.update("DELETE FROM rules_of_use WHERE version > 1");
  }

  private static RulesChange change() {
    return new RulesChange(
        Duration.ofHours(4),
        BatteryLevel.of(25),
        Duration.ofMinutes(20),
        Duration.ofMinutes(12),
        Duration.ZERO,
        Duration.ofMinutes(30),
        Money.of("2.00"),
        Map.of(VehicleType.E_BIKE, 4, VehicleType.E_SCOOTER, 9));
  }

  @Test
  void theMigrationSeedsVersionOneWithTheDefaults() {
    RulesOfUse first = rules.findCurrent().orElseThrow();

    assertThat(first.version()).isEqualTo(1);
    assertThat(first.createdBy()).isNull();
    assertThat(first.maxBookingDuration()).isEqualTo(Duration.ofHours(8));
    assertThat(first.minBattery()).isEqualTo(BatteryLevel.of(20));
    assertThat(first.startWindow()).isEqualTo(Duration.ofMinutes(15));
    assertThat(first.repositioningWindow()).isEqualTo(Duration.ofMinutes(10));
    assertThat(first.overtimeGrace()).isEqualTo(Duration.ofMinutes(5));
    assertThat(first.overtimeBlock()).isEqualTo(Duration.ofMinutes(15));
    assertThat(first.overtimeRatePerBlock()).isEqualTo(Money.of("1.50"));
    assertThat(first.deposit(VehicleType.E_BIKE)).isEqualTo(Money.of("20.00"));
    assertThat(first.deposit(VehicleType.E_SCOOTER)).isEqualTo(Money.of("30.00"));
    // The battery rule of the search (BR1): 20% and 5% per started 30 minutes, as before US14.
    assertThat(first.batteryRule().floorPercent()).isEqualTo(20);
    assertThat(first.batteryRule().requiredPercent(VehicleType.E_BIKE, 30)).isEqualTo(25);
    assertThat(first.batteryRule().requiredPercent(VehicleType.E_SCOOTER, 30)).isEqualTo(25);
  }

  @Test
  void save_nextVersion_isStoredWithEveryFieldAndBecomesTheCurrentOne() {
    Instant at = Instant.parse("2026-11-24T12:00:00Z");
    RulesOfUse next = rules.findCurrent().orElseThrow().revised(at, MANAGER, change());

    RulesOfUse saved = rules.save(next);

    assertThat(saved).isEqualTo(next);
    assertThat(rules.findCurrent()).contains(next);
    assertThat(next.version()).isEqualTo(2);
    assertThat(next.createdBy()).isEqualTo(MANAGER);
    assertThat(next.createdAt()).isEqualTo(at);
  }

  @Test
  void save_neverChangesTheOlderVersions() {
    RulesOfUse first = rules.findCurrent().orElseThrow();

    rules.save(first.revised(Instant.parse("2026-11-24T12:00:00Z"), MANAGER, change()));

    assertThat(rules.findAll()).last().isEqualTo(first);
  }

  @Test
  void findAll_isEveryVersionNewestFirst() {
    RulesOfUse first = rules.findCurrent().orElseThrow();
    RulesOfUse second =
        rules.save(first.revised(Instant.parse("2026-11-24T12:00:00Z"), MANAGER, change()));
    RulesOfUse third =
        rules.save(
            second.withDeposit(
                Instant.parse("2026-11-24T13:00:00Z"),
                MANAGER,
                VehicleType.E_BIKE,
                Money.of("25")));

    assertThat(rules.findAll()).extracting(RulesOfUse::version).containsExactly(3, 2, 1);
    assertThat(rules.findCurrent()).contains(third);
  }

  @Test
  void save_versionAlreadyStored_isReportedAsAConflictAndChangesNothing() {
    RulesOfUse first = rules.findCurrent().orElseThrow();
    RulesOfUse second = first.revised(Instant.parse("2026-11-24T12:00:00Z"), MANAGER, change());
    rules.save(second);

    // A second manager, who also read version 1, saves at the same moment.
    RulesOfUse rival =
        first.withDeposit(
            Instant.parse("2026-11-24T12:00:01Z"), MANAGER, VehicleType.E_BIKE, Money.of("99"));

    assertThatThrownBy(() -> rules.save(rival)).isInstanceOf(RulesVersionConflictException.class);
    assertThat(rules.findCurrent()).contains(second);
    assertThat(rules.findAll()).hasSize(2);
  }

  @Test
  void database_refusesRulesTheDomainWouldRefuse() {
    assertThatThrownBy(() ->
            jdbc.update("UPDATE rules_of_use SET max_booking_minutes = 1441 WHERE version = 1"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE rules_of_use SET max_booking_minutes = 0 WHERE version = 1"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
            jdbc.update("UPDATE rules_of_use SET min_battery_percent = 101 WHERE version = 1"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE rules_of_use SET min_battery_percent = -1 WHERE version = 1"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
            jdbc.update("UPDATE rules_of_use_vehicle_types SET deposit = -1 WHERE version = 1"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
