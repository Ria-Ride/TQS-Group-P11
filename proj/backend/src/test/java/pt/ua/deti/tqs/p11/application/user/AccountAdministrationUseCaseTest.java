package pt.ua.deti.tqs.p11.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.p11.application.auth.EmailAlreadyInUseException;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.ProtectedAccountException;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

@ExtendWith(MockitoExtension.class)
class AccountAdministrationUseCaseTest {

  private static final UUID NEW_ID = UUID.fromString("7a3e4f5d-6c7b-4180-8c9d-1e2f3a4b5c6d");
  private static final EmailAddress JOANA_EMAIL = new EmailAddress("joana.silva@example.com");

  private static final UUID RUI_ID = UUID.fromString("6f2d3e4c-5b6a-4079-9b8c-0d1e2f3a4b5c");
  private static final EmailAddress RUI_EMAIL = new EmailAddress("rui.costa@example.com");
  private static final User RUI =
      User.account(RUI_ID, "Rui Costa", RUI_EMAIL, "rui-hash", Role.OPERATOR, false);

  private static final UUID MARTA_ID = UUID.fromString("8b4f5a6e-7d8c-4291-9dae-2f3a4b5c6d7e");
  private static final User MARTA_ADMIN =
      User.account(
          MARTA_ID,
          "Marta Reis",
          new EmailAddress("marta.reis@example.com"),
          "marta-hash",
          Role.ADMIN,
          false);

  @Mock private UserRepository users;
  @Mock private PasswordHasher passwordHasher;

  private AccountAdministrationUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new AccountAdministrationUseCase(users, passwordHasher, () -> NEW_ID);
  }

  /** The repository stores what it gets and hands it back, like the real one. */
  private void saveReturnsWhatItGets() {
    when(users.save(any(User.class))).then(returnsFirstArg());
  }

  private static CreateAccountCommand joana(Role role, boolean fieldTechnician) {
    return new CreateAccountCommand(
        "Joana Silva", JOANA_EMAIL.value(), "Passw0rd!", role, fieldTechnician);
  }

  @Test
  void create_operatorAsFieldTechnician_savesAnActiveAccountWithTheHashedPassword() {
    when(users.existsByEmail(JOANA_EMAIL)).thenReturn(false);
    when(passwordHasher.hash("Passw0rd!")).thenReturn("joana-hash");
    saveReturnsWhatItGets();

    assertThat(useCase.create(joana(Role.OPERATOR, true)))
        .isEqualTo(
            new User(NEW_ID, "Joana Silva", JOANA_EMAIL, "joana-hash", Role.OPERATOR, true, true));
  }

  @Test
  void create_emailAlreadyInUse_isRefusedAndNothingIsSaved() {
    when(users.existsByEmail(JOANA_EMAIL)).thenReturn(true);

    assertThatThrownBy(() -> useCase.create(joana(Role.OPERATOR, false)))
        .isInstanceOf(EmailAlreadyInUseException.class);
    verify(users, never()).save(any());
  }

  /** Admins are created only by the seed or in the database. */
  @Test
  void create_admin_isRefusedAndNothingIsSaved() {
    assertThatThrownBy(() -> useCase.create(joana(Role.ADMIN, false)))
        .isInstanceOf(ProtectedAccountException.class);
    verify(users, never()).save(any());
  }

  @Test
  void changeRole_operatorToClientManager_savesTheNewRole() {
    when(users.findById(RUI_ID)).thenReturn(Optional.of(RUI));
    saveReturnsWhatItGets();

    assertThat(useCase.changeRole(RUI_ID, Role.CLIENT_MANAGER, false))
        .isEqualTo(
            new User(RUI_ID, "Rui Costa", RUI_EMAIL, "rui-hash", Role.CLIENT_MANAGER, false, true));
  }

  @Test
  void changeRole_unknownAccount_isNotFound() {
    when(users.findById(RUI_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.changeRole(RUI_ID, Role.CLIENT_MANAGER, false))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void changeRole_ofAnAdmin_isRefusedAndNothingIsSaved() {
    when(users.findById(MARTA_ID)).thenReturn(Optional.of(MARTA_ADMIN));

    assertThatThrownBy(() -> useCase.changeRole(MARTA_ID, Role.OPERATOR, false))
        .isInstanceOf(ProtectedAccountException.class);
    verify(users, never()).save(any());
  }

  @Test
  void setActive_false_savesTheAccountDeactivated() {
    when(users.findById(RUI_ID)).thenReturn(Optional.of(RUI));
    saveReturnsWhatItGets();

    assertThat(useCase.setActive(RUI_ID, false))
        .isEqualTo(
            new User(RUI_ID, "Rui Costa", RUI_EMAIL, "rui-hash", Role.OPERATOR, false, false));
  }

  @Test
  void setActive_true_savesTheAccountReactivated() {
    when(users.findById(RUI_ID)).thenReturn(Optional.of(RUI.deactivate()));
    saveReturnsWhatItGets();

    assertThat(useCase.setActive(RUI_ID, true)).isEqualTo(RUI);
  }

  @Test
  void setActive_unknownAccount_isNotFound() {
    when(users.findById(RUI_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.setActive(RUI_ID, false))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void setActive_falseOnAnAdmin_isRefusedAndNothingIsSaved() {
    when(users.findById(MARTA_ID)).thenReturn(Optional.of(MARTA_ADMIN));

    assertThatThrownBy(() -> useCase.setActive(MARTA_ID, false))
        .isInstanceOf(ProtectedAccountException.class);
    verify(users, never()).save(any());
  }
}
