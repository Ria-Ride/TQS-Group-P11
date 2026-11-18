package pt.ua.deti.tqs.p11.domain.user;

import java.util.Objects;
import java.util.UUID;

/**
 * A platform account. The password is only ever kept as a hash. Immutable: every change returns a
 * new User.
 */
public record User(
    UUID id,
    String name,
    EmailAddress email,
    String passwordHash,
    Role role,
    boolean fieldTechnician,
    boolean active) {

  public User {
    Objects.requireNonNull(id, "id");
    name = Objects.requireNonNull(name, "name").strip();
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(passwordHash, "passwordHash");
    Objects.requireNonNull(role, "role");
  }

  /** A guest account (BR10): no institutional identity, always END_USER. */
  public static User guest(UUID id, String name, EmailAddress email, String passwordHash) {
    return new User(id, name, email, passwordHash, Role.END_USER, false, true);
  }

  /** A new, active account with any role (created by an admin, US13). */
  public static User account(
      UUID id,
      String name,
      EmailAddress email,
      String passwordHash,
      Role role,
      boolean fieldTechnician) {
    return new User(id, name, email, passwordHash, role, fieldTechnician, true);
  }

  // KAN-77 red step: not implemented yet.
  public User changeRole(Role newRole, boolean newFieldTechnician) {
    return this;
  }

  // KAN-77 red step: not implemented yet.
  public User deactivate() {
    return this;
  }

  // KAN-77 red step: not implemented yet.
  public User activate() {
    return this;
  }

  /** Keeps the password hash out of logs and error messages. */
  @Override
  public String toString() {
    return "User[id=" + id + ", name=" + name + ", email=" + email.value() + ", role=" + role + "]";
  }
}
