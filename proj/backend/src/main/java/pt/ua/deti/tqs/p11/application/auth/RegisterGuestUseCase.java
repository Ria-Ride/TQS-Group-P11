package pt.ua.deti.tqs.p11.application.auth;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.application.user.PasswordHasher;
import pt.ua.deti.tqs.p11.application.user.UserRepository;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.User;

/** Creates a guest account and returns an access token for it (US01, BR10). */
public class RegisterGuestUseCase {

  private final UserRepository users;
  private final PasswordHasher passwordHasher;
  private final AccessTokenService tokens;
  private final Supplier<UUID> idGenerator;

  public RegisterGuestUseCase(
      UserRepository users,
      PasswordHasher passwordHasher,
      AccessTokenService tokens,
      Supplier<UUID> idGenerator) {
    this.users = Objects.requireNonNull(users, "users");
    this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
  }

  public AccessGrant register(RegisterGuestCommand command) {
    EmailAddress email = new EmailAddress(command.email());
    if (users.existsByEmail(email)) {
      throw new EmailAlreadyInUseException();
    }
    User guest =
        User.guest(
            idGenerator.get(), command.name(), email, passwordHasher.hash(command.password()));
    User saved = users.save(guest);
    return new AccessGrant(saved.id(), tokens.issue(saved.id(), saved.role()));
  }
}
