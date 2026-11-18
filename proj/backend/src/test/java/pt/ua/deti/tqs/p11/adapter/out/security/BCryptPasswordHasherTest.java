package pt.ua.deti.tqs.p11.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BCryptPasswordHasherTest {

  private static final String PASSWORD = "Passw0rd!";

  private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

  @Test
  void hash_isABcryptHashThatMatchesThePassword() {
    String hash = hasher.hash(PASSWORD);

    assertThat(hash).isNotEqualTo(PASSWORD).startsWith("$2");
    assertThat(new BCryptPasswordEncoder().matches(PASSWORD, hash)).isTrue();
  }

  @Test
  void hash_samePasswordTwice_givesDifferentHashes() {
    assertThat(hasher.hash(PASSWORD)).isNotEqualTo(hasher.hash(PASSWORD));
  }
}
