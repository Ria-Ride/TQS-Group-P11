package pt.ua.deti.tqs.p11.domain.user;

import java.util.UUID;

/** A platform account. The password is only ever kept as a hash. */
public record User(UUID id, String name, EmailAddress email, String passwordHash, Role role) {

  /** A guest account (BR10): no institutional identity, always END_USER. */
  public static User guest(UUID id, String name, EmailAddress email, String passwordHash) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}