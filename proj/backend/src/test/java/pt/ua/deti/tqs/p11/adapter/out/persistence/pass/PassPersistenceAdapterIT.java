package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.application.pass.PassRepository;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.pass.Pass;

/** Issued passes (BR8, BR9) against a real PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PassPersistenceAdapterIT {

  private static final UUID DAY_ID = UUID.fromString("7a1e0000-0000-4000-8000-000000000001");
  private static final UUID MULTI_DAY_ID = UUID.fromString("7a1e0000-0000-4000-8000-000000000003");
  private static final UUID DEMO_STAY_ID = UUID.fromString("57a90000-0000-4000-8000-000000000001");
  private static final Instant MONDAY = Instant.parse("2026-11-23T09:00:00Z");
  private static final Instant TUESDAY = Instant.parse("2026-11-24T09:00:00Z");

  @Autowired private PassRepository passes;
  @Autowired private JdbcTemplate jdbc;

  private UUID ana;
  private UUID rui;

  private UUID account(String email) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, role)"
            + " VALUES (?, 'Pass Tester', ?, 'x', 'END_USER')",
        id,
        email);
    return id;
  }

  /**
   * Before and after each test, so no pass points to a test account when other tests in this
   * context delete every user.
   */
  @AfterEach
  void removeTheRowsOfThisTest() {
    jdbc.update(
        "DELETE FROM passes WHERE account_id IN (SELECT id FROM users WHERE email LIKE 'pass.it.%')");
    jdbc.update("DELETE FROM users WHERE email LIKE 'pass.it.%'");
  }

  @BeforeEach
  void twoAccountsWithoutPasses() {
    removeTheRowsOfThisTest();
    ana = account("pass.it.ana@example.com");
    rui = account("pass.it.rui@example.com");
  }

  private static Pass pass(UUID account, UUID typeId, String code, UUID stay, Instant from) {
    return new Pass(
        UUID.randomUUID(),
        account,
        typeId,
        code,
        stay,
        Money.of("22.50"),
        from,
        from.plusSeconds(72 * 3600));
  }

  @Test
  void save_thenFindByAccount_returnsEveryFieldOfThePass() {
    Pass pass = pass(ana, MULTI_DAY_ID, "MULTI_DAY", DEMO_STAY_ID, MONDAY);

    passes.save(pass);

    assertThat(passes.findByAccount(ana)).containsExactly(pass);
  }

  @Test
  void save_withoutAStay_keepsNoStay() {
    Pass pass = pass(ana, DAY_ID, "DAY", null, MONDAY);

    passes.save(pass);

    assertThat(passes.findByAccount(ana))
        .singleElement()
        .satisfies(p -> assertThat(p.stayId()).isEmpty());
  }

  @Test
  void findByAccount_returnsOnlyThatAccountsPassesNewestFirst() {
    Pass older = pass(ana, DAY_ID, "DAY", null, MONDAY);
    Pass newer = pass(ana, DAY_ID, "DAY", null, TUESDAY);
    passes.save(older);
    passes.save(newer);
    passes.save(pass(rui, DAY_ID, "DAY", null, MONDAY));

    assertThat(passes.findByAccount(ana)).containsExactly(newer, older);
  }

  @Test
  void findByAccount_noPasses_isEmpty() {
    assertThat(passes.findByAccount(ana)).isEmpty();
  }

  /** BR8 also holds for rows edited directly in the database. */
  @Test
  void database_refusesAPassThatEndsWhenItStarts() {
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO passes (id, account_id, pass_type_id, price, valid_from, valid_to)"
                    + " VALUES (?, ?, ?, 10.00, ?, ?)",
                UUID.randomUUID(),
                ana,
                DAY_ID,
                Timestamp.from(MONDAY),
                Timestamp.from(MONDAY)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void database_refusesANegativePrice() {
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO passes (id, account_id, pass_type_id, price, valid_from, valid_to)"
                    + " VALUES (?, ?, ?, -0.01, ?, ?)",
                UUID.randomUUID(),
                ana,
                DAY_ID,
                Timestamp.from(MONDAY),
                Timestamp.from(TUESDAY)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
