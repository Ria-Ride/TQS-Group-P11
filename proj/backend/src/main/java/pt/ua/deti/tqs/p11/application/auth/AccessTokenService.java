package pt.ua.deti.tqs.p11.application.auth;

import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.user.Role;

/** Port: issues and verifies access tokens. Reused by the security filter (US13). */
public interface AccessTokenService {

  /** Issues a signed token for the user, valid for the configured time. */
  String issue(UUID userId, Role role);

  /** Returns the claims if the token is authentic and not expired, otherwise empty. */
  Optional<TokenClaims> verify(String token);
}