package pt.ua.deti.tqs.p11.application.user;

/** Port: one-way hashing of passwords (BCrypt in production). */
public interface PasswordHasher {

  String hash(String rawPassword);

  /** True if the raw password is the one that produced the hash. */
  boolean matches(String rawPassword, String hash);
}
