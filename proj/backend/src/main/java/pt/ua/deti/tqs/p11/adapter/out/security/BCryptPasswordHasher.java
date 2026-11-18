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

  @Override
  public boolean matches(String rawPassword, String hash) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
