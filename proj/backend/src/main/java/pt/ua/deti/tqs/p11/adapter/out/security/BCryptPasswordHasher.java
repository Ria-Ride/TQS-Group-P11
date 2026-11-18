package pt.ua.deti.tqs.p11.adapter.out.security;

import pt.ua.deti.tqs.p11.application.user.PasswordHasher;

/** PasswordHasher with BCrypt (salted, deliberately slow). */
public class BCryptPasswordHasher implements PasswordHasher {

  @Override
  public String hash(String rawPassword) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}