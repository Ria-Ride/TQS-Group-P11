package pt.ua.deti.tqs.p11.application.auth;

import pt.ua.deti.tqs.p11.application.user.PasswordHasher;
import pt.ua.deti.tqs.p11.application.user.UserRepository;

/** Checks a user's credentials and returns an access token (US01). */
public class LoginUseCase {

  public LoginUseCase(
      UserRepository users, PasswordHasher passwordHasher, AccessTokenService tokens) {}

  public AccessGrant login(LoginCommand command) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
