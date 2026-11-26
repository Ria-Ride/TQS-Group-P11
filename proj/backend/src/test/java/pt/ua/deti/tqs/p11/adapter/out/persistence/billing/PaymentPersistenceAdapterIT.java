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

/** Pass payments (BR10) and booking deposits (BR11), against a real PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentPersistenceAdapterIT {

  private static final UUID DAY_ID = UUID.fromString("7a1e0000-0000-4000-8000-000000000001");
  private static final String EMAIL = "payment.it@example.com";
  private static final Instant AT = Instant.parse("2026-11-24T10:15:30Z");

  @Autowired private PaymentRepository payments;
  @Autowired private JdbcTemplate jdbc;

  private UUID ana;
  private UUID pass;
  private UUID booking;

  /** Before and after each test; children first, so no row points to the test account. */
  @AfterEach
  void removeTheRowsOfThisTest() {
    String mine = "(SELECT id FROM users WHERE email = '" + EMAIL + "')";
    jdbc.update("DELETE FROM payments WHERE account_id IN " + mine);
    jdbc.update("DELETE FROM bookings WHERE account_id IN " + mine);
    jdbc.update("DELETE FROM passes WHERE account_id IN " + mine);
    jdbc.update("DELETE FROM vehicles WHERE code = 'pay-it-bike'");
    jdbc.update("DELETE FROM hubs WHERE code = 'pay-it-hub'");
    jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
  }

  @BeforeEach
  void anAccountWithAPassAndABooking() {
    removeTheRowsOfThisTest();
    ana = UUID.randomUUID();
    pass = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, role)"
            + " VALUES (?, 'Pay Tester', ?, 'x', 'END_USER')",
        ana,
        EMAIL);
    jdbc.update(
        "INSERT INTO passes (id, account_id, pass_type_id, price, valid_from, valid_to)"
            + " VALUES (?, ?, ?, 10.00, ?, ?)",
        pass,
        ana,
        DAY_ID,
        Timestamp.from(AT),
        Timestamp.from(AT.plusSeconds(86_400)));
    booking = insertActiveBooking();
  }

  /** An Active booking of a bike at a hub, with every row it points to. */
  private UUID insertActiveBooking() {
    UUID hub = UUID.randomUUID();
    UUID bike = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO hubs (id, code, name, latitude, longitude, capacity)"
            + " VALUES (?, 'pay-it-hub', 'Hub', 40.6405, -8.6538, 10)",
        hub);
    jdbc.update(
        "INSERT INTO vehicles (id, code, type, state, hub_id) VALUES (?, 'pay-it-bike', 'E_BIKE',"
            + " 'IN_USE', NULL)",
        bike);
    jdbc.update(
        "INSERT INTO bookings (id, account_id, vehicle_id, pass_id, start_hub_id, planned_start,"
            + " planned_end, deposit_reference, deposit_amount, start_window_seconds,"
            + " overtime_grace_seconds, overtime_block_seconds, overtime_price_per_block, state,"
            + " started_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'au_1', 30.00, 900, 600, 900, 2.00,"
            + " 'ACTIVE', ?)",
        id,
        ana,
        bike,
        pass,
        hub,
        Timestamp.from(AT),
        Timestamp.from(AT.plusSeconds(3600)),
        Timestamp.from(AT));
    return id;
  }

  private Map<String, Object> row(UUID id) {
    return jdbc.queryForMap(
        "SELECT account_id, kind, pass_id, booking_id, amount, state, gateway_reference,"
            + " failure_reason, created_at FROM payments WHERE id = ?",
        id);
  }

  // ------------------------------------------------------------- passes (US03)

  @Test
  void save_captured_storesTheReferenceAndThePass() {
    UUID id = UUID.randomUUID();

    payments.save(Payment.captured(id, ana, Money.of("9.00"), "ch_1", pass, AT));

    assertThat(row(id))
        .containsEntry("account_id", ana)
        .containsEntry("kind", "PASS")
        .containsEntry("pass_id", pass)
        .containsEntry("booking_id", null)
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
        .containsEntry("kind", "PASS")
        .containsEntry("pass_id", null)
        .containsEntry("state", "DECLINED")
        .containsEntry("failure_reason", "timeout");
  }

  /** BR10 also holds for rows written directly in the database. */
  @Test
  void database_refusesADeclinedPaymentWithAPass() {
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO payments (id, account_id, kind, pass_id, amount, state,"
                    + " failure_reason, created_at)"
                    + " VALUES (?, ?, 'PASS', ?, 10.00, 'DECLINED', 'card_declined', ?)",
                UUID.randomUUID(),
                ana,
                pass,
                Timestamp.from(AT)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ------------------------------------------------------------- deposits (US04)

  @Test
  void save_authorizedDeposit_storesTheReferenceAndTheBooking() {
    UUID id = UUID.randomUUID();

    payments.save(Payment.depositAuthorized(id, ana, Money.of("30.00"), "au_1", booking, AT));

    assertThat(row(id))
        .containsEntry("kind", "DEPOSIT")
        .containsEntry("state", "AUTHORIZED")
        .containsEntry("booking_id", booking)
        .containsEntry("pass_id", null)
        .containsEntry("amount", new BigDecimal("30.00"))
        .containsEntry("gateway_reference", "au_1");
  }

  @Test
  void save_declinedDeposit_storesTheReasonAndNoBooking() {
    UUID id = UUID.randomUUID();

    payments.save(Payment.depositDeclined(id, ana, Money.of("30.00"), "card_declined", AT));

    assertThat(row(id))
        .containsEntry("kind", "DEPOSIT")
        .containsEntry("state", "DECLINED")
        .containsEntry("booking_id", null)
        .containsEntry("failure_reason", "card_declined");
  }

  @Test
  void save_releasedDepositOfABookingThatWasNotCreated_storesNoBooking() {
    UUID id = UUID.randomUUID();

    payments.save(Payment.depositReleased(id, ana, Money.of("30.00"), "au_2", null, AT));

    assertThat(row(id))
        .containsEntry("state", "RELEASED")
        .containsEntry("booking_id", null)
        .containsEntry("gateway_reference", "au_2");
  }

  /** BR11 for rows written directly: a held deposit always belongs to a booking. */
  @Test
  void database_refusesAnAuthorizedDepositWithoutABooking() {
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO payments (id, account_id, kind, amount, state, gateway_reference,"
                    + " created_at) VALUES (?, ?, 'DEPOSIT', 30.00, 'AUTHORIZED', 'au_9', ?)",
                UUID.randomUUID(),
                ana,
                Timestamp.from(AT)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** One deposit per booking. */
  @Test
  void database_refusesASecondDepositForTheSameBooking() {
    payments.save(
        Payment.depositAuthorized(UUID.randomUUID(), ana, Money.of("30.00"), "au_1", booking, AT));
    Payment second =
        Payment.depositAuthorized(UUID.randomUUID(), ana, Money.of("30.00"), "au_2", booking, AT);

    assertThatThrownBy(() -> payments.save(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
