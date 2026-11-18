package pt.ua.deti.tqs.p11.adapter.out.token;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.application.auth.AccessTokenService;
import pt.ua.deti.tqs.p11.application.auth.TokenClaims;
import pt.ua.deti.tqs.p11.domain.user.Role;

public class JwtTokenService implements AccessTokenService {

  public JwtTokenService(String secret, Duration timeToLive, Clock clock) {}

  @Override
  public String issue(UUID userId, Role role) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public Optional<TokenClaims> verify(String token) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}