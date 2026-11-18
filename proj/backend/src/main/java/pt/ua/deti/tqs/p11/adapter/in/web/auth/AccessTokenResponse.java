package pt.ua.deti.tqs.p11.adapter.in.web.auth;

import java.util.UUID;
import pt.ua.deti.tqs.p11.application.auth.AccessGrant;

/** Response of registration and login: send the token as "Authorization: Bearer <token>". */
public record AccessTokenResponse(UUID userId, String accessToken, String tokenType) {

  static AccessTokenResponse from(AccessGrant grant) {
    return new AccessTokenResponse(grant.userId(), grant.accessToken(), "Bearer");
  }
}
