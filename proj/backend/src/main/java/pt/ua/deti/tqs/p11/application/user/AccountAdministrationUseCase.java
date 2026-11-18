package pt.ua.deti.tqs.p11.application.user;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

/**
 * What a platform admin can do with accounts (US13): create, list, change role, (de)activate. One
 * class for the four, as they share the actor and the dependencies.
 */
public class AccountAdministrationUseCase {

  private final UserRepository users;
  private final PasswordHasher passwordHasher;
  private final Supplier<UUID> idGenerator;

  public AccountAdministrationUseCase(
      UserRepository users, PasswordHasher passwordHasher, Supplier<UUID> idGenerator) {
    this.users = Objects.requireNonNull(users, "users");
    this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
  }

  // KAN-77 red step: not implemented yet.
  public User create(CreateAccountCommand command) {
    return null;
  }

  // KAN-77 red step: not implemented yet.
  public List<User> list() {
    return List.of();
  }

  // KAN-77 red step: not implemented yet.
  public User changeRole(UUID id, Role role, boolean fieldTechnician) {
    return null;
  }

  // KAN-77 red step: not implemented yet.
  public User setActive(UUID id, boolean active) {
    return null;
  }
}