package pt.ua.deti.tqs.p11.domain.user;

import java.util.Objects;
import java.util.UUID;

/**
 * A platform account. The password is only ever kept as a hash. Immutable: every change returns a
 * new User, and the constructor checks the rules, so an invalid User cannot exist.
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
    if (fieldTechnician && role != Role.OPERATOR) {
      throw new FieldTechnicianRequiresOperatorException();
    }
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

  /**
   * Gives the account a new role and technician flag. Admins are managed in the database only, so
   * an admin's role cannot be changed and no one can be made an admin here.
   */
  public User changeRole(Role newRole, boolean newFieldTechnician) {
    if (role == Role.ADMIN || newRole == Role.ADMIN) {
      throw new ProtectedAccountException("Admin roles are managed directly in the database");
    }
    return new User(id, name, email, passwordHash, newRole, newFieldTechnician, active);
  }

  /** A deactivated account can no longer log in or use its tokens. Admins cannot be deactivated. */
  public User deactivate() {
    if (role == Role.ADMIN) {
      throw new ProtectedAccountException("Admin accounts cannot be deactivated");
    }
    return new User(id, name, email, passwordHash, role, fieldTechnician, false);
  }

  public User activate() {
    return new User(id, name, email, passwordHash, role, fieldTechnician, true);
  }

  /** Keeps the password hash out of logs and error messages. */
  @Override
  public String toString() {
    return "User[id="
        + id
        + ", name="
        + name
        + ", email="
        + email.value()
        + ", role="
        + role
        + ", fieldTechnician="
        + fieldTechnician
        + ", active="
        + active
        + "]";
  }
}
