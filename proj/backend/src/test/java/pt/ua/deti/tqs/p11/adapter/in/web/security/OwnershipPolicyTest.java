package pt.ua.deti.tqs.p11.adapter.in.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

class OwnershipPolicyTest {

  private static final UUID ANA_ID = UUID.fromString("2b3c4d5e-6f70-4182-9394-a5b6c7d8e9f0");
  private static final UUID BRUNO_ID = UUID.fromString("3c4d5e6f-7081-4293-a4b5-c6d7e8f90a1b");

  private final OwnershipPolicy ownership = new OwnershipPolicy();

  private static TestingAuthenticationToken authenticatedAs(UUID userId) {
    // Authorities make the token "authenticated", like one built from a valid JWT.
    return new TestingAuthenticationToken(userId.toString(), null, "ROLE_END_USER");
  }

  @Test
  void isSelf_ownId_isTrue() {
    assertThat(ownership.isSelf(ANA_ID, authenticatedAs(ANA_ID))).isTrue();
  }

  @Test
  void isSelf_anotherUsersId_isFalse() {
    assertThat(ownership.isSelf(BRUNO_ID, authenticatedAs(ANA_ID))).isFalse();
  }

  @Test
  void isSelf_noAuthentication_isFalse() {
    assertThat(ownership.isSelf(ANA_ID, null)).isFalse();
  }

  @Test
  void isSelf_notAuthenticated_isFalse() {
    TestingAuthenticationToken notAuthenticated =
        new TestingAuthenticationToken(ANA_ID.toString(), null);

    assertThat(ownership.isSelf(ANA_ID, notAuthenticated)).isFalse();
  }
}