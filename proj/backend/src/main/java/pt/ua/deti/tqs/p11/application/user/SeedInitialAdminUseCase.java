package pt.ua.deti.tqs.p11.application.user;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** KAN-77 red step: not implemented yet. */
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

  public boolean seed(String name, String email, String password) {
    return false;
  }
}
