package pt.ua.deti.tqs.p11.application.user;

import java.util.Objects;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.user.User;

/** Reads one account. Who may call it is decided at the API (BR23). */
public class GetUserProfileUseCase {

  private final UserRepository users;

  public GetUserProfileUseCase(UserRepository users) {
    this.users = Objects.requireNonNull(users, "users");
  }

  public User get(UUID userId) {
    return users.findById(userId).orElseThrow(UserNotFoundException::new);
  }
}
