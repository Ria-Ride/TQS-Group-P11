package pt.ua.deti.tqs.p11.adapter.in.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

@ExtendWith(MockitoExtension.class)
class ActiveAccountAuthenticationConverterTest {

  private static final UUID RUI_ID = UUID.fromString("6f2d3e4c-5b6a-4079-9b8c-0d1e2f3a4b5c");

  @Mock private UserRepository users;
  @InjectMocks private ActiveAccountAuthenticationConverter converter;

  private static Jwt token(String subject, String roleClaim) {
    return Jwt.withTokenValue("token")
        .header("alg", "HS256")
        .subject(subject)
        .claim("role", roleClaim)
        .build();
  }

  @Test
  void convert_activeAccount_takesTheRoleFromTheDatabaseNotFromTheToken() {
    when(users.findById(RUI_ID))
        .thenReturn(
            Optional.of(
                User.account(
                    RUI_ID,
                    "Rui Costa",
                    new EmailAddress("rui.costa@example.com"),
                    "rui-hash",
                    Role.OPERATOR,
                    false)));

    AbstractAuthenticationToken authentication =
        converter.convert(token(RUI_ID.toString(), "END_USER"));

    assertThat(authentication).isNotNull();
    assertThat(authentication.getName()).isEqualTo(RUI_ID.toString());
    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_OPERATOR");
  }

  @Test
  void convert_subjectThatIsNotAUserId_isRefused() {
    assertThatThrownBy(() -> converter.convert(token("not-a-uuid", "END_USER")))
        .isInstanceOf(InvalidBearerTokenException.class);
  }
}
