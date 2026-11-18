package pt.ua.deti.tqs.p11.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.application.user.PasswordHasher;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

@ExtendWith(MockitoExtension.class)
class RegisterGuestUseCaseTest {

  private static final UUID NEW_ID = UUID.fromString("9d3e1f2a-6b7c-4d8e-8f90-a1b2c3d4e5f6");
  private static final RegisterGuestCommand ANA =
      new RegisterGuestCommand("Ana Sousa", "Ana.Sousa@Example.com", "Passw0rd!");

  @Mock private UserRepository users;
  @Mock private PasswordHasher passwordHasher;
  @Mock private AccessTokenService tokens;

  private RegisterGuestUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new RegisterGuestUseCase(users, passwordHasher, tokens, () -> NEW_ID);
  }

  @Test
  void register_newEmail_savesEndUserWithNormalizedEmailAndHashedPassword() {
    when(passwordHasher.hash("Passw0rd!")).thenReturn("hashed-password");
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    useCase.register(ANA);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue())
        .isEqualTo(
            new User(
                NEW_ID,
                "Ana Sousa",
                new EmailAddress("ana.sousa@example.com"),
                "hashed-password",
                Role.END_USER));
  }

  @Test
  void register_newEmail_returnsTokenIssuedForTheNewEndUser() {
    when(passwordHasher.hash(any())).thenReturn("hashed-password");
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(tokens.issue(NEW_ID, Role.END_USER)).thenReturn("token-for-new-user");

    assertThat(useCase.register(ANA)).isEqualTo(new AccessGrant(NEW_ID, "token-for-new-user"));
  }

  @Test
  void register_emailInUseWithDifferentCase_isRefusedAndNothingIsSaved() {
    when(users.existsByEmail(new EmailAddress("ana.sousa@example.com"))).thenReturn(true);
    RegisterGuestCommand sameEmailUpperCase =
        new RegisterGuestCommand("Ana S.", "ANA.SOUSA@example.com", "Other123!");

    assertThatThrownBy(() -> useCase.register(sameEmailUpperCase))
        .isInstanceOf(EmailAlreadyInUseException.class);
    verify(users, never()).save(any());
    verifyNoInteractions(tokens);
  }
}