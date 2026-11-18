package pt.ua.deti.tqs.p11.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailAddressTest {

  @Test
  void value_isTrimmedAndLowerCased() {
    assertThat(new EmailAddress("  Ana.Sousa@Example.COM ").value())
        .isEqualTo("ana.sousa@example.com");
  }

  @Test
  void addressesDifferingOnlyInCase_areEqual() {
    assertThat(new EmailAddress("ANA.SOUSA@example.com"))
        .isEqualTo(new EmailAddress("ana.sousa@example.com"));
  }

  @Test
  void nullValue_isRefused() {
    assertThatThrownBy(() -> new EmailAddress(null)).isInstanceOf(NullPointerException.class);
  }
}