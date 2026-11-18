package pt.ua.deti.tqs.p11.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class UserTest {

  private static final UUID ID = UUID.fromString("0b7f3c1e-2a4d-4c8e-9f10-5e6d7c8b9a01");
  private static final EmailAddress EMAIL = new EmailAddress("ana.sousa@example.com");

  private static User operator(boolean fieldTechnician) {
    return User.account(ID, "Rui Costa", EMAIL, "hash", Role.OPERATOR, fieldTechnician);
  }

  private static User admin() {
    return User.account(ID, "Marta Reis", EMAIL, "hash", Role.ADMIN, false);
  }

  @Test
  void guest_hasRoleEndUser() {
    assertThat(User.guest(ID, "Ana Sousa", EMAIL, "hash").role()).isEqualTo(Role.END_USER);
  }

  @Test
  void guest_nameIsTrimmed() {
    assertThat(User.guest(ID, "  Ana Sousa ", EMAIL, "hash").name()).isEqualTo("Ana Sousa");
  }

  @Test
  void guest_isActiveAndNotAFieldTechnician() {
    User guest = User.guest(ID, "Ana Sousa", EMAIL, "hash");

    assertThat(guest.active()).isTrue();
    assertThat(guest.fieldTechnician()).isFalse();
  }

  @Test
  void toString_doesNotRevealThePasswordHash() {
    assertThat(User.guest(ID, "Ana Sousa", EMAIL, "secret-hash").toString())
        .doesNotContain("secret-hash");
  }

  @Test
  void missingId_isRefused() {
    assertThatThrownBy(() -> User.guest(null, "Ana Sousa", EMAIL, "hash"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void account_operatorAsFieldTechnician_isCreatedActiveWithTheFlag() {
    User technician = operator(true);

    assertThat(technician.role()).isEqualTo(Role.OPERATOR);
    assertThat(technician.fieldTechnician()).isTrue();
    assertThat(technician.active()).isTrue();
  }

  /** BR20: only an OPERATOR can be a field technician. */
  @ParameterizedTest
  @EnumSource(value = Role.class, names = "OPERATOR", mode = EnumSource.Mode.EXCLUDE)
  void account_fieldTechnicianWithAnotherRole_isRefused(Role role) {
    assertThatThrownBy(() -> User.account(ID, "Rui Costa", EMAIL, "hash", role, true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void changeRole_operatorToClientManager_changesOnlyTheRole() {
    assertThat(operator(false).changeRole(Role.CLIENT_MANAGER, false))
        .isEqualTo(new User(ID, "Rui Costa", EMAIL, "hash", Role.CLIENT_MANAGER, false, true));
  }

  @Test
  void changeRole_canMakeAnOperatorAFieldTechnician() {
    assertThat(operator(false).changeRole(Role.OPERATOR, true).fieldTechnician()).isTrue();
  }

  @Test
  void changeRole_awayFromOperatorKeepingTheTechnicianFlag_isRefused() {
    assertThatThrownBy(() -> operator(true).changeRole(Role.END_USER, true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** Admin accounts are managed directly in the database, never through the API. */
  @Test
  void changeRole_ofAnAdmin_isRefused() {
    assertThatThrownBy(() -> admin().changeRole(Role.OPERATOR, false))
        .isInstanceOf(ProtectedAccountException.class);
  }

  @Test
  void changeRole_toAdmin_isRefused() {
    assertThatThrownBy(() -> operator(false).changeRole(Role.ADMIN, false))
        .isInstanceOf(ProtectedAccountException.class);
  }

  @Test
  void deactivate_makesTheAccountInactive() {
    assertThat(operator(false).deactivate().active()).isFalse();
  }

  @Test
  void activate_reactivatesADeactivatedAccount() {
    User deactivated = new User(ID, "Rui Costa", EMAIL, "hash", Role.OPERATOR, false, false);

    assertThat(deactivated.activate().active()).isTrue();
  }

  /** Covers the admin deactivating themselves too: whoever calls the API is an admin. */
  @Test
  void deactivate_anAdmin_isRefused() {
    assertThatThrownBy(() -> admin().deactivate()).isInstanceOf(ProtectedAccountException.class);
  }
}
