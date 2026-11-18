package pt.ua.deti.tqs.p11.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

  private static final UUID ID = UUID.fromString("0b7f3c1e-2a4d-4c8e-9f10-5e6d7c8b9a01");
  private static final EmailAddress EMAIL = new EmailAddress("ana.sousa@example.com");

  @Test
  void guest_hasRoleEndUser() {
    assertThat(User.guest(ID, "Ana Sousa", EMAIL, "hash").role()).isEqualTo(Role.END_USER);
  }

  @Test
  void guest_nameIsTrimmed() {
    assertThat(User.guest(ID, "  Ana Sousa ", EMAIL, "hash").name()).isEqualTo("Ana Sousa");
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
}
