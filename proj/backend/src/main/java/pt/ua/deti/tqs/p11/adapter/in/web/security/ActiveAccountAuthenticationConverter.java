package pt.ua.deti.tqs.p11.adapter.in.web.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.p11.application.user.UserRepository;

/** KAN-77 red step: not implemented and not wired into the security filter yet. */
@Component
public class ActiveAccountAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final UserRepository users;

  ActiveAccountAuthenticationConverter(UserRepository users) {
    this.users = users;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    return null;
  }
}
