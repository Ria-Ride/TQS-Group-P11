package pt.ua.deti.tqs.p11.application.user;

import pt.ua.deti.tqs.p11.domain.user.Role;

/** An admin's request to create an account (US13). The password is still in plain text here. */
public record CreateAccountCommand(
    String name, String email, String password, Role role, boolean fieldTechnician) {

  /** Keeps the password out of logs and error messages. */
  @Override
  public String toString() {
    return "CreateAccountCommand[name="
        + name
        + ", email="
        + email
        + ", role="
        + role
        + ", fieldTechnician="
        + fieldTechnician
        + "]";
  }
}
