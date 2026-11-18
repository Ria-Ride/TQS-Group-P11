package pt.ua.deti.tqs.p11.adapter.out.persistence.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.ua.deti.tqs.p11.TestcontainersConfiguration;
import pt.ua.deti.tqs.p11.application.auth.EmailAlreadyInUseException;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.User;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserPersistenceAdapterIT {

  private static final UUID ANA_ID = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000001");
  private static final UUID OTHER_ID = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000002");
  private static final EmailAddress ANA_EMAIL = new EmailAddress("ana.sousa@example.com");

  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

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
}
