package pt.ua.deti.tqs.p11.application.user;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

/**
 * Creates the first ADMIN of an environment from its configuration (US13), so no credential is ever
 * committed. Runs on every start: an existing account with that email is never changed, so a
 * restart cannot reset a password or promote anyone (promotions happen in the database only).
 */
public class SeedInitialAdminUseCase {

  private final UserRepository users;
  private final PasswordHasher passwordHasher;
  private final Supplier<UUID> idGenerator;

  public SeedInitialAdminUseCase(
      UserRepository users, PasswordHasher passwordHasher, Supplier<UUID> idGenerator) {
    this.users = Objects.requireNonNull(users, "users");
    this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
  }

  /** Returns true if the admin was created, false if an account with that email already existed. */
  public boolean seed(String name, String email, String password) {
    EmailAddress address = new EmailAddress(email);
    if (users.existsByEmail(address)) {
      return false;
    }
    users.save(
        User.account(
            idGenerator.get(), name, address, passwordHasher.hash(password), Role.ADMIN, false));
    return true;
  }
}
