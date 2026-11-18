package pt.ua.deti.tqs.p11.application.auth;

/** Unknown e-mail or wrong password. Deliberately does not say which. */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Invalid email or password");
  }
}
