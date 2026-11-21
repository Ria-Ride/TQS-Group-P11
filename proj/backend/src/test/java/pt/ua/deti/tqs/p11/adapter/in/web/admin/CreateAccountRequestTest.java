package pt.ua.deti.tqs.p11.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.p11.domain.user.Role;

class CreateAccountRequestTest {

  @Test
  void toString_doesNotRevealThePassword() {
    CreateAccountRequest request =
        new CreateAccountRequest(
            "Rui Costa", "rui.costa@example.com", "Secr3t!xy", Role.OPERATOR, false);

    assertThat(request.toString()).doesNotContain("Secr3t!xy");
  }
}
