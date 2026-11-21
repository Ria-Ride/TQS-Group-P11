package pt.ua.deti.tqs.p11.adapter.in.web.admin;

import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

/** An account as the admin sees it: never includes the password hash. */
public record AdminUserResponse(
    UUID id, String name, String email, Role role, boolean fieldTechnician, boolean active) {

  static AdminUserResponse from(User user) {
    return new AdminUserResponse(
        user.id(),
        user.name(),
        user.email().value(),
        user.role(),
        user.fieldTechnician(),
        user.active());
  }
}
