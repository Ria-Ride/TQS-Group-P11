package pt.ua.deti.tqs.p11.adapter.in.web.user;

import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.user.Role;
import pt.ua.deti.tqs.p11.domain.user.User;

/** Public view of an account: never includes the password hash. */
public record UserProfileResponse(UUID id, String name, String email, Role role) {

  static UserProfileResponse from(User user) {
    return new UserProfileResponse(user.id(), user.name(), user.email().value(), user.role());
  }
}
