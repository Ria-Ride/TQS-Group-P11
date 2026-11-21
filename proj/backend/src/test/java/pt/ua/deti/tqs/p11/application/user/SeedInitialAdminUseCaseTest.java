package pt.ua.deti.tqs.p11.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

@ExtendWith(MockitoExtension.class)
class SeedInitialAdminUseCaseTest {

  private static final UUID NEW_ID = UUID.fromString("9c5a6b7f-8e9d-43a2-8ebf-3a4b5c6d7e8f");
  private static final EmailAddress EMAIL = new EmailAddress("root.admin@example.com");

  @Mock private UserRepository users;
  @Mock private PasswordHasher passwordHasher;

  private SeedInitialAdminUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new SeedInitialAdminUseCase(users, passwordHasher, () -> NEW_ID);
  }

  @Test
  void seed_noAccountWithThatEmail_createsAnActiveAdmin() {
    when(users.existsByEmail(EMAIL)).thenReturn(false);
    when(passwordHasher.hash("Adm1nPass!")).thenReturn("admin-hash");

    boolean created = useCase.seed("Platform Admin", EMAIL.value(), "Adm1nPass!");

    assertThat(created).isTrue();
    verify(users)
        .save(new User(NEW_ID, "Platform Admin", EMAIL, "admin-hash", Role.ADMIN, false, true));
  }

  /** Runs on every start: once the account exists it is left as it is (even if not an admin). */
  @Test
  void seed_emailAlreadyInUse_changesNothing() {
    when(users.existsByEmail(EMAIL)).thenReturn(true);

    boolean created = useCase.seed("Platform Admin", EMAIL.value(), "Adm1nPass!");

    assertThat(created).isFalse();
    verify(users, never()).save(any());
  }
}
