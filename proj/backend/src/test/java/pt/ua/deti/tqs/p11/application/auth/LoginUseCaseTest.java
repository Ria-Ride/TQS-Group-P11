package pt.ua.deti.tqs.p11.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.application.user.PasswordHasher;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

  private static final UUID ANA_ID = UUID.fromString("5e1c2d3b-4a59-4f68-8a7b-9c0d1e2f3a4b");
  private static final EmailAddress ANA_EMAIL = new EmailAddress("ana.sousa@example.com");
  private static final User ANA = User.guest(ANA_ID, "Ana Sousa", ANA_EMAIL, "ana-hash");

  @Mock private UserRepository users;
  @Mock private PasswordHasher passwordHasher;
  @Mock private AccessTokenService tokens;

  private LoginUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new LoginUseCase(users, passwordHasher, tokens);
  }

  @Test
  void login_correctPasswordAndEmailInAnotherCase_returnsTokenForTheUser() {
    when(users.findByEmail(ANA_EMAIL)).thenReturn(Optional.of(ANA));
    when(passwordHasher.matches("Passw0rd!", "ana-hash")).thenReturn(true);
    when(tokens.issue(ANA_ID, Role.END_USER)).thenReturn("token-for-ana");

    AccessGrant grant = useCase.login(new LoginCommand("ANA.Sousa@example.com", "Passw0rd!"));

    assertThat(grant).isEqualTo(new AccessGrant(ANA_ID, "token-for-ana"));
  }

  @Test
  void login_operator_tokenCarriesTheStoredRole() {
    UUID operatorId = UUID.fromString("6f2d3e4c-5b6a-4079-9b8c-0d1e2f3a4b5c");
    EmailAddress operatorEmail = new EmailAddress("rui.costa@example.com");
    User operator = new User(operatorId, "Rui Costa", operatorEmail, "rui-hash", Role.OPERATOR);
    when(users.findByEmail(operatorEmail)).thenReturn(Optional.of(operator));
    when(passwordHasher.matches("Secr3t!xy", "rui-hash")).thenReturn(true);
    when(tokens.issue(operatorId, Role.OPERATOR)).thenReturn("token-for-rui");

    assertThat(useCase.login(new LoginCommand("rui.costa@example.com", "Secr3t!xy")).accessToken())
        .isEqualTo("token-for-rui");
  }

  @Test
  void login_wrongPassword_isRefusedWithoutAToken() {
    when(users.findByEmail(ANA_EMAIL)).thenReturn(Optional.of(ANA));
    when(passwordHasher.matches("WrongPass1!", "ana-hash")).thenReturn(false);

    assertThatThrownBy(
            () -> useCase.login(new LoginCommand("ana.sousa@example.com", "WrongPass1!")))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(tokens);
  }

  @Test
  void login_unknownEmail_isRefusedWithoutAToken() {
    when(users.findByEmail(new EmailAddress("bruno.lima@example.com")))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.login(new LoginCommand("bruno.lima@example.com", "Passw0rd!")))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(tokens);
  }
}
