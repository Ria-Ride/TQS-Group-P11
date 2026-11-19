package pt.ua.deti.tqs.p11.adapter.in.web.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.user.User;

/**
 * Turns a valid JWT into the request's authentication, reading the account from the database on
 * every request (US13): an unknown or deactivated account is refused with 401, and the role comes
 * from the database, not from the token, so a role change applies at once. One lookup by primary
 * key per request.
 */
@Component
public class ActiveAccountAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final UserRepository users;

  ActiveAccountAuthenticationConverter(UserRepository users) {
    this.users = users;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    User user =
        parseId(jwt.getSubject())
            .flatMap(users::findById)
            .filter(User::active)
            // An AuthenticationException: the bearer token filter answers it with 401.
            .orElseThrow(() -> new InvalidBearerTokenException("Unknown or deactivated account"));
    return new JwtAuthenticationToken(
        jwt, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())), jwt.getSubject());
  }

  private static Optional<UUID> parseId(String subject) {
    try {
      return Optional.of(UUID.fromString(subject));
    } catch (IllegalArgumentException | NullPointerException e) {
      return Optional.empty();
    }
  }
}
