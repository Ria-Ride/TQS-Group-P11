package pt.ua.deti.tqs.p11.application.auth;

import java.util.Objects;
import pt.ua.deti.tqs.p11.application.user.PasswordHasher;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.User;

/** Checks a user's credentials and returns an access token (US01). */
public class LoginUseCase {

  private final UserRepository users;
  private final PasswordHasher passwordHasher;
  private final AccessTokenService tokens;

  public LoginUseCase(
      UserRepository users, PasswordHasher passwordHasher, AccessTokenService tokens) {
    this.users = Objects.requireNonNull(users, "users");
    this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    this.tokens = Objects.requireNonNull(tokens, "tokens");
  }

  /**
   * Unknown e-mail, wrong password and deactivated account give the same exception, so the response
   * does not reveal which e-mails have an account or whether it is active.
   */
  public AccessGrant login(LoginCommand command) {
    User user =
        users
            .findByEmail(new EmailAddress(command.email()))
            .filter(found -> passwordHasher.matches(command.password(), found.passwordHash()))
            .filter(User::active)
            .orElseThrow(InvalidCredentialsException::new);
    return new AccessGrant(user.id(), tokens.issue(user.id(), user.role()));
  }
}
