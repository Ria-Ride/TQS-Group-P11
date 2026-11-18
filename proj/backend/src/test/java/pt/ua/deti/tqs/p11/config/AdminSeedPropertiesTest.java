package pt.ua.deti.tqs.p11.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AdminSeedPropertiesTest {

  @Test
  void isConfigured_withEmailAndPassword_isTrue() {
    assertThat(
            new AdminSeedProperties("Platform Admin", "root.admin@example.com", "Adm1nPass!")
                .isConfigured())
        .isTrue();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void isConfigured_withoutEmail_isFalse(String email) {
    assertThat(new AdminSeedProperties("Platform Admin", email, "Adm1nPass!").isConfigured())
        .isFalse();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void isConfigured_withoutPassword_isFalse(String password) {
    assertThat(
            new AdminSeedProperties("Platform Admin", "root.admin@example.com", password)
                .isConfigured())
        .isFalse();
  }
}
