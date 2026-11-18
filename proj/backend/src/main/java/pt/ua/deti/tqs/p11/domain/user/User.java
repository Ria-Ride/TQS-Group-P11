package pt.ua.deti.tqs.p11.domain.user;

import java.util.Objects;
import java.util.UUID;

/** A platform account. The password is only ever kept as a hash. */
public record User(UUID id, String name, EmailAddress email, String passwordHash, Role role) {

  public User {
    Objects.requireNonNull(id, "id");
    name = Objects.requireNonNull(name, "name").strip();
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(passwordHash, "passwordHash");
    Objects.requireNonNull(role, "role");
  }

  /** A guest account (BR10): no institutional identity, always END_USER. */
  public static User guest(UUID id, String name, EmailAddress email, String passwordHash) {
    return new User(id, name, email, passwordHash, Role.END_USER);
  }

  /** Keeps the password hash out of logs and error messages. */
  @Override
  public String toString() {
    return "User[id=" + id + ", name=" + name + ", email=" + email.value() + ", role=" + role + "]";
  }
}
