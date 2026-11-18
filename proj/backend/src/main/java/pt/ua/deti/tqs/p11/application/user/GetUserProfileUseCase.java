package pt.ua.deti.tqs.p11.application.user;

import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.user.User;

/** Reads one account. Who may call it is decided at the API (BR23). */
public class GetUserProfileUseCase {

  public GetUserProfileUseCase(UserRepository users) {}

  public User get(UUID userId) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}