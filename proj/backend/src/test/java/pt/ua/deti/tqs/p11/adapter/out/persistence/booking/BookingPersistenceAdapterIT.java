package pt.ua.deti.tqs.p11.adapter.out.persistence.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
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
import pt.ua.deti.tqs.p11.application.booking.BookingRepository;
import pt.ua.deti.tqs.p11.domain.billing.Money;
import pt.ua.deti.tqs.p11.domain.booking.Booking;
import pt.ua.deti.tqs.p11.domain.booking.BookingRefusal;
import pt.ua.deti.tqs.p11.domain.booking.BookingRefusedException;
import pt.ua.deti.tqs.p11.domain.booking.BookingRules;
import pt.ua.deti.tqs.p11.domain.booking.BookingState;
import pt.ua.deti.tqs.p11.domain.booking.BookingTerms;

/** Bookings and the rules they keep (BR21), against a real PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BookingPersistenceAdapterIT {

  private static final UUID DAY_ID = UUID.fromString("7a1e0000-0000-4000-8000-000000000001");
  private static final String EMAIL = "booking.it@example.com";
  private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
  private static final BookingRules RULES =
      new BookingRules(
          Money.of("30.00"),
          Duration.ofMinutes(15),
          Duration.ofMinutes(15),
          Duration.ofMinutes(10),
          Duration.ofMinutes(15),
          Money.of("2.00"));

  @Autowired private BookingRepository bookings;
  @Autowired private JdbcTemplate jdbc;

  private UUID ana;
  private UUID pass;
  private UUID hub;
  private UUID otherHub;
  private UUID bike;

  /** Before and after each test; children first, because bookings point to all the others. */
  @AfterEach
  void removeTheRowsOfThisTest() {
    String mine = "(SELECT id FROM users WHERE email = '" + EMAIL + "')";
    jdbc.update("DELETE FROM payments WHERE account_id IN " + mine);
    jdbc.update("DELETE FROM bookings WHERE account_id IN " + mine);
    jdbc.update("DELETE FROM passes WHERE account_id IN " + mine);
    jdbc.update("DELETE FROM vehicles WHERE code LIKE 'bk-it-%'");
    jdbc.update("DELETE FROM hubs WHERE code LIKE 'bk-it-%'");
    jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
  }

  @BeforeEach
  void anAccountWithAPassAndAVehicleAtAHub() {
    removeTheRowsOfThisTest();
    ana = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, role)"
            + " VALUES (?, 'Booking Tester', ?, 'x', 'END_USER')",
        ana,
        EMAIL);
    hub = insertHub("bk-it-hub-1");
    otherHub = insertHub("bk-it-hub-2");
    bike = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO vehicles (id, code, type, state, hub_id, battery_percent)"
            + " VALUES (?, 'bk-it-bike-1', 'E_BIKE', 'AVAILABLE', ?, 100)",
        bike,
        hub);
    pass = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO passes (id, account_id, pass_type_id, price, valid_from, valid_to)"
            + " VALUES (?, ?, ?, 10.00, ?, ?)",
        pass,
        ana,
        DAY_ID,
        Timestamp.from(NOW),
        Timestamp.from(NOW.plus(Duration.ofDays(1))));
  }

  private UUID insertHub(String code) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO hubs (id, code, name, latitude, longitude, capacity)"
            + " VALUES (?, ?, ?, 40.6405, -8.6538, 10)",
        id,
        code,
        "Hub " + code);
    return id;
  }

  private BookingTerms terms(Instant start, Duration length) {
    return new BookingTerms(ana, bike, pass, hub, start, start.plus(length));
  }

  private Booking active(Instant start, String depositReference) {
    return Booking.startNow(
        UUID.randomUUID(), terms(start, Duration.ofMinutes(60)), RULES, depositReference);
  }

  // ------------------------------------------------------------- save and read

  @Test
  void save_activeBooking_isFoundWithEveryField() {
    Booking booking = active(NOW, "au_1");

    bookings.save(booking);

    assertThat(bookings.findById(booking.id())).contains(booking);
  }

  @Test
  void save_existingBooking_updatesItInPlace() {
    Booking booking = active(NOW, "au_1");
    bookings.save(booking);
    Booking completed = booking.complete(otherHub, NOW.plus(Duration.ofMinutes(70)));

    bookings.save(completed);

    assertThat(bookings.findById(booking.id())).contains(completed);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE id = ?", Integer.class, booking.id()))
        .isEqualTo(1);
  }

  @Test
  void save_cancelledBooking_keepsTheReason() {
    Booking reserved =
        Booking.reserve(
            UUID.randomUUID(),
            terms(NOW.plus(Duration.ofHours(1)), Duration.ofMinutes(60)),
            RULES,
            "au_2");
    bookings.save(reserved);

    bookings.save(reserved.cancel("vehicle fault"));

    assertThat(bookings.findById(reserved.id()))
        .get()
        .extracting(Booking::state, Booking::cancelReason)
        .containsExactly(BookingState.CANCELLED, "vehicle fault");
  }

  @Test
  void findById_unknownId_isEmpty() {
    assertThat(bookings.findById(UUID.randomUUID())).isEmpty();
  }

  /** BR21: each booking reads back the rules it was created with, not the newest ones. */
  @Test
  void findById_returnsTheRulesTheBookingWasCreatedWith() {
    Booking first = active(NOW, "au_1");
    bookings.save(first);
    bookings.save(first.complete(otherHub, NOW.plus(Duration.ofMinutes(70))));
    BookingRules changed =
        new BookingRules(
            Money.of("50.00"),
            Duration.ofMinutes(20),
            Duration.ofMinutes(15),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Money.of("3.50"));
    Booking second =
        Booking.startNow(
            UUID.randomUUID(),
            terms(NOW.plus(Duration.ofHours(2)), Duration.ofMinutes(60)),
            changed,
            "au_3");

    bookings.save(second);

    assertThat(bookings.findById(first.id())).get().extracting(Booking::rules).isEqualTo(RULES);
    assertThat(bookings.findById(second.id())).get().extracting(Booking::rules).isEqualTo(changed);
  }

  /** BR6: the tourist's Reserved and Active bookings, which a new booking must not overlap. */
  @Test
  void findOngoingByAccount_returnsOnlyTheReservedAndActiveBookingsOfTheAccount() {
    Booking completed = active(NOW, "au_1");
    bookings.save(completed);
    bookings.save(completed.complete(otherHub, NOW.plus(Duration.ofMinutes(70))));
    Booking riding = active(NOW.plus(Duration.ofHours(2)), "au_2");
    bookings.save(riding);

    assertThat(bookings.findOngoingByAccount(ana)).containsExactly(riding);
    assertThat(bookings.findOngoingByAccount(UUID.randomUUID())).isEmpty();
  }

  // ------------------------------------------------------------- US05 queries

  private Booking reserved(Instant start, String depositReference) {
    return Booking.reserve(
        UUID.randomUUID(), terms(start, Duration.ofMinutes(60)), RULES, depositReference);
  }

  /** BR7: the candidates for expiry; whether each is past its window is decided by Booking. */
  @Test
  void findReservedStartingBefore_returnsOnlyReservedBookingsPlannedBeforeThatMoment() {
    Booking due = reserved(NOW.plus(Duration.ofHours(1)), "au_1");
    bookings.save(due);
    Instant afterItsStart = NOW.plus(Duration.ofHours(1)).plusSeconds(1);

    assertThat(bookings.findReservedStartingBefore(afterItsStart)).containsExactly(due);
    assertThat(bookings.findReservedStartingBefore(NOW.plus(Duration.ofHours(1)))).isEmpty();
  }

  @Test
  void findReservedStartingBefore_leavesOutBookingsThatAreNotReserved() {
    Booking cancelled = reserved(NOW.plus(Duration.ofHours(1)), "au_1");
    bookings.save(cancelled);
    bookings.save(cancelled.cancel("plans changed"));
    bookings.save(active(NOW, "au_2"));

    assertThat(bookings.findReservedStartingBefore(NOW.plus(Duration.ofDays(1)))).isEmpty();
  }

  /** BR18: the reservation a fault of the vehicle cancels. */
  @Test
  void findReservedOfVehicle_findsOnlyItsReservedBooking() {
    Booking held = reserved(NOW.plus(Duration.ofHours(1)), "au_1");
    bookings.save(held);

    assertThat(bookings.findReservedOfVehicle(bike)).contains(held);
    assertThat(bookings.findReservedOfVehicle(UUID.randomUUID())).isEmpty();
  }

  @Test
  void findReservedOfVehicle_activeBookingOfTheVehicle_isNotAReservation() {
    bookings.save(active(NOW, "au_1"));

    assertThat(bookings.findReservedOfVehicle(bike)).isEmpty();
  }

  /** GET /bookings: every booking of the account, latest planned start first. */
  @Test
  void findByAccount_returnsEveryBookingOfTheAccountLatestFirst() {
    Booking earlier = active(NOW, "au_1");
    bookings.save(earlier);
    Booking completed = earlier.complete(otherHub, NOW.plus(Duration.ofMinutes(70)));
    bookings.save(completed);
    Booking later = reserved(NOW.plus(Duration.ofHours(3)), "au_2");
    bookings.save(later);

    assertThat(bookings.findByAccount(ana)).containsExactly(later, completed);
    assertThat(bookings.findByAccount(UUID.randomUUID())).isEmpty();
  }

  // ------------------------------------------------------------- database rules

  /**
   * BR3: at most one Reserved or Active booking per vehicle, whoever writes the row. The vehicle
   * lock of the use case makes this unreachable through it; if a future path forgets the lock, the
   * index still refuses, and the refusal is the same one the tourist gets for a taken vehicle.
   */
  @Test
  void save_secondOngoingBookingOfTheSameVehicle_isRefusedAsNotAvailable() {
    bookings.save(active(NOW, "au_1"));
    Booking another = active(NOW.plus(Duration.ofMinutes(5)), "au_2");

    assertThatThrownBy(() -> bookings.save(another))
        .isInstanceOfSatisfying(
            BookingRefusedException.class,
            e -> assertThat(e.reason()).isEqualTo(BookingRefusal.VEHICLE_NOT_AVAILABLE));
  }

  @Test
  void database_acceptsANewBookingOnceThePreviousOneIsCompleted() {
    Booking first = active(NOW, "au_1");
    bookings.save(first);
    bookings.save(first.complete(otherHub, NOW.plus(Duration.ofMinutes(70))));
    Booking next = active(NOW.plus(Duration.ofHours(2)), "au_2");

    bookings.save(next);

    assertThat(bookings.findById(next.id())).contains(next);
  }

  /** The state rules of Booking also hold for rows written directly in the database. */
  @Test
  void database_refusesAnActiveBookingWithoutAStartTime() {
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO bookings (id, account_id, vehicle_id, pass_id, start_hub_id,"
                    + " planned_start, planned_end, deposit_reference, deposit_amount,"
                    + " start_window_seconds, repositioning_window_seconds, overtime_grace_seconds, overtime_block_seconds,"
                    + " overtime_price_per_block, state)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, 'au_9', 30.00, 900, 900, 600, 900, 2.00,"
                    + " 'ACTIVE')",
                UUID.randomUUID(),
                ana,
                bike,
                pass,
                hub,
                Timestamp.from(NOW),
                Timestamp.from(NOW.plus(Duration.ofMinutes(60)))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ------------------------------------------------------------- US06: the end of a ride

  /** BR15, BR21: the first full-hub refusal and the repositioning window are read back. */
  @Test
  void save_rideRefusedAtAFullHubThenEnded_keepsTheAttemptAndTheWindow() {
    Booking riding = active(NOW, "au_1");
    bookings.save(riding);
    Booking tried = riding.recordFullHubAttempt(NOW.plus(Duration.ofMinutes(65)));
    bookings.save(tried);

    Booking ended = tried.complete(otherHub, NOW.plus(Duration.ofMinutes(70)));
    bookings.save(ended);

    assertThat(bookings.findById(riding.id())).contains(ended);
    assertThat(bookings.findById(riding.id()).orElseThrow().rules().repositioningWindow())
        .isEqualTo(RULES.repositioningWindow());
  }

  @Test
  void database_refusesAFullHubAttemptOnAReservation() {
    Booking reserved = reserved(NOW.plus(Duration.ofHours(1)), "au_1");
    bookings.save(reserved);

    assertThatThrownBy(() ->
            jdbc.update(
                "UPDATE bookings SET full_hub_attempt_at = ? WHERE id = ?",
                Timestamp.from(NOW),
                reserved.id()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
