package pt.ua.deti.tqs.p11.adapter.out.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pt.ua.deti.tqs.p11.application.user.PasswordHasher;

/** PasswordHasher with BCrypt (salted, deliberately slow). */
public class BCryptPasswordHasher implements PasswordHasher {

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  @Override
  public String hash(String rawPassword) {
    return encoder.encode(rawPassword);
  }
}