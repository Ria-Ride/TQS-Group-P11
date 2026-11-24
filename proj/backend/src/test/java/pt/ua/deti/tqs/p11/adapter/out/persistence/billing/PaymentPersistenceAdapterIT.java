package pt.ua.deti.tqs.p11.adapter.out.persistence.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
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
import pt.ua.deti.tqs.p11.application.billing.PaymentRepository;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.billing.Payment;

/** Payments, approved or not (BR10), against a real PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentPersistenceAdapterIT {

  private static final UUID DAY_ID = UUID.fromString("7a1e0000-0000-4000-8000-000000000001");
  private static final Instant AT = Instant.parse("2026-11-24T10:15:30Z");

  @Autowired private PaymentRepository payments;
  @Autowired private JdbcTemplate jdbc;

  private UUID ana;
  private UUID pass;

  /** Before and after each test, so no row points to the test account when users are deleted. */
  @AfterEach
  void removeTheRowsOfThisTest() {
    String mine = "(SELECT id FROM users WHERE email = 'payment.it@example.com')";
    jdbc.update("DELETE FROM payments WHERE account_id IN " + mine);
    jdbc.update("DELETE FROM passes WHERE account_id IN " + mine);
    jdbc.update("DELETE FROM users WHERE email = 'payment.it@example.com'");
  }

  @BeforeEach
  void anAccountWithAPass() {
    removeTheRowsOfThisTest();
    ana = UUID.randomUUID();
    pass = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, role)"
            + " VALUES (?, 'Pay Tester', 'payment.it@example.com', 'x', 'END_USER')",
        ana);
    jdbc.update(
        "INSERT INTO passes (id, account_id, pass_type_id, price, valid_from, valid_to)"
            + " VALUES (?, ?, ?, 10.00, ?, ?)",
        pass,
        ana,
        DAY_ID,
        Timestamp.from(AT),
        Timestamp.from(AT.plusSeconds(86_400)));
  }

  private Map<String, Object> row(UUID id) {
    return jdbc.queryForMap(
        "SELECT account_id, pass_id, amount, state, gateway_reference, failure_reason, created_at"
            + " FROM payments WHERE id = ?",
        id);
  }

  @Test
  void save_captured_storesTheReferenceAndThePass() {
    UUID id = UUID.randomUUID();

    payments.save(Payment.captured(id, ana, Money.of("9.00"), "ch_1", pass, AT));

    assertThat(row(id))
        .containsEntry("account_id", ana)
        .containsEntry("pass_id", pass)
        .containsEntry("amount", new BigDecimal("9.00"))
        .containsEntry("state", "CAPTURED")
        .containsEntry("gateway_reference", "ch_1")
        .containsEntry("failure_reason", null);
  }

  @Test
  void save_declined_storesTheReasonAndNoPass() {
    UUID id = UUID.randomUUID();

    payments.save(Payment.declined(id, ana, Money.of("10.00"), "timeout", AT));

    assertThat(row(id))
        .containsEntry("pass_id", null)
        .containsEntry("state", "DECLINED")
        .containsEntry("failure_reason", "timeout");
  }

  /** BR10 also holds for rows written directly in the database. */
  @Test
  void database_refusesADeclinedPaymentWithAPass() {
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO payments (id, account_id, pass_id, amount, state, failure_reason,"
                    + " created_at) VALUES (?, ?, ?, 10.00, 'DECLINED', 'card_declined', ?)",
                UUID.randomUUID(),
                ana,
                pass,
                Timestamp.from(AT)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
