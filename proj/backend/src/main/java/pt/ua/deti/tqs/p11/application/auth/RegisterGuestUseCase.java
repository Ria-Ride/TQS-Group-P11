package pt.ua.deti.tqs.p11.application.auth;

import java.util.UUID;
import java.util.function.Supplier;
import pt.ua.deti.tqs.p11.application.user.PasswordHasher;
import pt.ua.deti.tqs.p11.application.user.UserRepository;

/** Creates a guest account and returns an access token for it (US01, BR10). */
public class RegisterGuestUseCase {

  public RegisterGuestUseCase(
      UserRepository users,
      PasswordHasher passwordHasher,
      AccessTokenService tokens,
      Supplier<UUID> idGenerator) {}

  public AccessGrant register(RegisterGuestCommand command) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}