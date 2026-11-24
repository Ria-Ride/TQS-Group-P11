package pt.ua.deti.tqs.p11.adapter.out.persistence.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.application.pass.StayRepository;
import pt.ua.deti.tqs.p11.domain.pass.Stay;

/** Stays and the account their code is linked to (BR9), against a real PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StayPersistenceAdapterIT {

  private static final UUID HOTEL_ID = UUID.fromString("5a7e0000-0000-4000-8000-000000000001");
  private static final UUID STAY_ID = UUID.fromString("57a90000-0000-4000-8000-0000000000a1");
  private static final LocalDate CHECK_IN = LocalDate.parse("2026-11-23");
  private static final LocalDate CHECK_OUT = LocalDate.parse("2026-11-25");

  @Autowired private StayRepository stays;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate transaction;

  private UUID ana;

  /**
   * Only rows of this test are removed, before and after each test: the seeded demo stays remain,
   * and no stay points to the test account when other tests in this context delete every user.
   */
  @AfterEach
  void removeTheRowsOfThisTest() {
    jdbc.update("DELETE FROM stays WHERE code LIKE 'IT-%'");
    jdbc.update("DELETE FROM users WHERE email = 'stay.it@example.com'");
  }

  @BeforeEach
  void oneStayAndOneAccount() {
    removeTheRowsOfThisTest();
    ana = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, role)"
            + " VALUES (?, 'Ana Tester', 'stay.it@example.com', 'x', 'END_USER')",
        ana);
    jdbc.update(
        "INSERT INTO stays (id, code, accommodation_id, check_in, check_out)"
            + " VALUES (?, 'IT-1001', ?, ?, ?)",
        STAY_ID,
        HOTEL_ID,
        CHECK_IN,
        CHECK_OUT);
  }

  private Optional<Stay> findLocked(String code) {
    return transaction.execute(status -> stays.findByCodeForUpdate(code));
  }

  @Test
  void findByCodeForUpdate_returnsEveryFieldWithTheHotelDiscount() {
    Stay stay = findLocked("IT-1001").orElseThrow();

    assertThat(stay.id()).isEqualTo(STAY_ID);
    assertThat(stay.code()).isEqualTo("IT-1001");
    assertThat(stay.checkIn()).isEqualTo(CHECK_IN);
    assertThat(stay.checkOut()).isEqualTo(CHECK_OUT);
    assertThat(stay.accommodation().name()).isEqualTo("Hotel Ria Azul");
    assertThat(stay.discountPercent()).isEqualTo(10);
    assertThat(stay.redeemedBy()).isEmpty();
  }

  @Test
  void findByCodeForUpdate_unknownCode_isEmpty() {
    assertThat(findLocked("IT-9999")).isEmpty();
  }

  /**
   * BR9, K3: while one transaction holds the stay, a second one asking for it waits, and gets it
   * only after the first commits. This is what makes two purchases with one code run one after the
   * other, so only one account can link it. Without the lock the second would not wait.
   */
  @Test
  void findByCodeForUpdate_whileAnotherTransactionHoldsTheStay_waitsUntilItCommits()
      throws Exception {
    CountDownLatch firstHoldsTheLock = new CountDownLatch(1);
    CountDownLatch firstMayCommit = new CountDownLatch(1);

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<?> first =
          pool.submit(() ->
              transaction.executeWithoutResult(status -> {
                stays.findByCodeForUpdate("IT-1001").orElseThrow();
                firstHoldsTheLock.countDown();
                await(firstMayCommit);
              }));
      assertThat(firstHoldsTheLock.await(10, TimeUnit.SECONDS)).isTrue();

      Future<Optional<Stay>> second = pool.submit(() -> findLocked("IT-1001"));

      assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
          .as("the second transaction waits for the lock")
          .isInstanceOf(TimeoutException.class);
      firstMayCommit.countDown();
      first.get(10, TimeUnit.SECONDS);
      assertThat(second.get(10, TimeUnit.SECONDS)).isPresent();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  @Test
  void save_storesTheLinkedAccount() {
    transaction.executeWithoutResult(status -> {
      Stay stay = stays.findByCodeForUpdate("IT-1001").orElseThrow();
      stay.redeemBy(ana, CHECK_IN);
      stays.save(stay);
    });

    assertThat(findLocked("IT-1001").orElseThrow().redeemedBy()).contains(ana);
  }

  @Test
  void save_afterUnlink_freesTheCode() {
    jdbc.update("UPDATE stays SET redeemed_by = ? WHERE id = ?", ana, STAY_ID);

    transaction.executeWithoutResult(status -> {
      Stay stay = stays.findByCodeForUpdate("IT-1001").orElseThrow();
      stay.unlink();
      stays.save(stay);
    });

    assertThat(findLocked("IT-1001").orElseThrow().redeemedBy()).isEmpty();
  }

  /** Lookups upper-case the code, so a lower-case row could never be found. */
  @Test
  void database_refusesALowerCaseCode() {
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO stays (id, code, accommodation_id, check_in, check_out)"
                    + " VALUES (?, 'it-lower', ?, ?, ?)",
                UUID.randomUUID(),
                HOTEL_ID,
                CHECK_IN,
                CHECK_OUT))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void database_refusesACheckOutBeforeCheckIn() {
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO stays (id, code, accommodation_id, check_in, check_out)"
                    + " VALUES (?, 'IT-1002', ?, ?, ?)",
                UUID.randomUUID(),
                HOTEL_ID,
                CHECK_OUT,
                CHECK_IN))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
