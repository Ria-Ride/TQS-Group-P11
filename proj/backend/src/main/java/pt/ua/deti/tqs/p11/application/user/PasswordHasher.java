package pt.ua.deti.tqs.p11.application.user;

/** Port: one-way hashing of passwords (BCrypt in production). */
public interface PasswordHasher {

  String hash(String rawPassword);
}