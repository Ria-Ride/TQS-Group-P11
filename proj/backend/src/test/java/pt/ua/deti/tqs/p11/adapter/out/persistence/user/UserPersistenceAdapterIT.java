package pt.ua.deti.tqs.p11.adapter.out.persistence.user;

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
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.application.TransactionRunner;
import pt.ua.deti.tqs.p11.application.auth.EmailAlreadyInUseException;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserPersistenceAdapterIT {

  private static final UUID ANA_ID = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000001");
  private static final UUID OTHER_ID = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000002");
  private static final EmailAddress ANA_EMAIL = new EmailAddress("ana.sousa@example.com");

  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionRunner transactions;

  @BeforeEach
  void emptyUsersTable() {
    jdbc.update("DELETE FROM users");
  }

  @Test
  void save_storesEveryFieldOfTheGuest() {
    users.save(User.guest(ANA_ID, "Ana Sousa", ANA_EMAIL, "hashed-password"));

    Map<String, Object> row =
        jdbc.queryForMap("SELECT name, email, password_hash, role FROM users WHERE id = ?", ANA_ID);
    assertThat(row)
        .containsEntry("name", "Ana Sousa")
        .containsEntry("email", "ana.sousa@example.com")
        .containsEntry("password_hash", "hashed-password")
        .containsEntry("role", "END_USER");
  }

  @Test
  void existsByEmail_isTrueOnlyForStoredAddresses() {
    users.save(User.guest(ANA_ID, "Ana Sousa", ANA_EMAIL, "hashed-password"));

    assertThat(users.existsByEmail(ANA_EMAIL)).isTrue();
    assertThat(users.existsByEmail(new EmailAddress("bruno.lima@example.com"))).isFalse();
  }

  @Test
  void save_secondAccountWithSameEmail_isRefusedByTheDatabase() {
    users.save(User.guest(ANA_ID, "Ana Sousa", ANA_EMAIL, "hashed-password"));
    User sameEmail = User.guest(OTHER_ID, "Ana S.", ANA_EMAIL, "other-hash");

    // Bypasses existsByEmail, as two concurrent registrations would.
    assertThatThrownBy(() -> users.save(sameEmail)).isInstanceOf(EmailAlreadyInUseException.class);
  }

  @Test
  void findByEmail_returnsTheStoredUser() {
    User ana = User.guest(ANA_ID, "Ana Sousa", ANA_EMAIL, "hashed-password");
    users.save(ana);

    assertThat(users.findByEmail(ANA_EMAIL)).contains(ana);
  }

  @Test
  void findByEmail_unknownAddress_isEmpty() {
    assertThat(users.findByEmail(new EmailAddress("bruno.lima@example.com"))).isEmpty();
  }

  @Test
  void findById_returnsTheStoredUser() {
    User ana = User.guest(ANA_ID, "Ana Sousa", ANA_EMAIL, "hashed-password");
    users.save(ana);

    assertThat(users.findById(ANA_ID)).contains(ana);
  }

  @Test
  void findById_unknownId_isEmpty() {
    assertThat(users.findById(OTHER_ID)).isEmpty();
  }

  // US13 (KAN-77): technician flag, account status, updates and listing.

  @Test
  void save_keepsTheTechnicianFlagAndTheStatus() {
    User technician =
        User.account(ANA_ID, "Ana Sousa", ANA_EMAIL, "hashed-password", Role.OPERATOR, true)
            .deactivate();
    users.save(technician);

    assertThat(users.findById(ANA_ID)).contains(technician);
  }

  @Test
  void save_existingAccount_updatesItInPlace() {
    users.save(
        User.account(ANA_ID, "Ana Sousa", ANA_EMAIL, "hashed-password", Role.OPERATOR, false));
    User changed =
        users.findById(ANA_ID).orElseThrow().changeRole(Role.OPERATOR, true).deactivate();

    users.save(changed);

    assertThat(users.findById(ANA_ID)).contains(changed);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
  }

  @Test
  void findAll_returnsEveryAccountOrderedByName() {
    users.save(
        User.guest(OTHER_ID, "Rui Costa", new EmailAddress("rui.costa@example.com"), "rui-hash"));
    users.save(User.guest(ANA_ID, "Ana Sousa", ANA_EMAIL, "hashed-password"));

    assertThat(users.findAll()).extracting(User::name).containsExactly("Ana Sousa", "Rui Costa");
  }

  /** BR20 also holds for rows edited directly in the database (e.g. by whoever manages admins). */
  @Test
  void database_refusesAFieldTechnicianWhoIsNotAnOperator() {
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO users (id, name, email, password_hash, role, field_technician,"
                    + " active) VALUES (?, 'Ana Sousa', 'ana.sousa@example.com', 'hash',"
                    + " 'END_USER', TRUE, TRUE)",
                ANA_ID))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /**
   * US04 (BR6): an account is locked while its booking is created, so two simultaneous bookings of
   * one tourist run one after the other. The lock itself is shown by the concurrency test of
   * KAN-702; here, that it finds the account inside a transaction.
   */
  @Test
  void findByIdForUpdate_insideATransaction_findsTheAccount() {
    users.save(User.guest(ANA_ID, "Ana Sousa", ANA_EMAIL, "hashed-password"));

    Optional<User> locked = transactions.inTransaction(() -> users.findByIdForUpdate(ANA_ID));

    assertThat(locked).isPresent();
  }

  @Test
  void findByIdForUpdate_unknownId_isEmpty() {
    Optional<User> locked = transactions.inTransaction(() -> users.findByIdForUpdate(OTHER_ID));

    assertThat(locked).isEmpty();
  }
}
