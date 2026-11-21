package pt.ua.deti.tqs.p11.application.auth;

/** Raised when a guest tries to register with an e-mail that already has an account. */
public class EmailAlreadyInUseException extends RuntimeException {

  public EmailAlreadyInUseException() {
    super("E-mail already in use");
  }
}
