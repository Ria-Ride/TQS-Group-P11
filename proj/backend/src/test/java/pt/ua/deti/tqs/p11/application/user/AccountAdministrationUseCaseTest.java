package pt.ua.deti.tqs.p11.application.user;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.application.auth.EmailAlreadyInUseException;
import pt.ua.deti.tqs.p11.domain.user.EmailAddress;
import pt.ua.deti.tqs.p11.domain.user.ProtectedAccountException;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

/**
 * What a platform admin can do with accounts (US13): create, list, change role, (de)activate. One
 * class for the four, as they share the actor and the dependencies. The rules on admin accounts
 * and on the technician flag (BR20) live in User; this class loads, applies and saves.
 */
public class AccountAdministrationUseCaseTest {

  private final UserRepository users;
  private final PasswordHasher passwordHasher;
  private final Supplier<UUID> idGenerator;

  public AccountAdministrationUseCaseTest(
      UserRepository users, PasswordHasher passwordHasher, Supplier<UUID> idGenerator) {
    this.users = Objects.requireNonNull(users, "users");
    this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
  }

  /** Creates an active account with any role but ADMIN (admins are made only in the database). */
  public User create(CreateAccountCommand command) {
    if (command.role() == Role.ADMIN) {
      throw new ProtectedAccountException("Admin accounts are created directly in the database");
    }
    EmailAddress email = new EmailAddress(command.email());
    if (users.existsByEmail(email)) {
      throw new EmailAlreadyInUseException();
    }
    return users.save(
        User.account(
            idGenerator.get(),
            command.name(),
            email,
            passwordHasher.hash(command.password()),
            command.role(),
            command.fieldTechnician()));
  }

  public List<User> list() {
    return users.findAll();
  }

  public User changeRole(UUID id, Role role, boolean fieldTechnician) {
    return users.save(find(id).changeRole(role, fieldTechnician));
  }

  public User setActive(UUID id, boolean active) {
    User user = find(id);
    return users.save(active ? user.activate() : user.deactivate());
  }

  private User find(UUID id) {
    return users.findById(id).orElseThrow(UserNotFoundException::new);
  }
}