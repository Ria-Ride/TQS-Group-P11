package pt.ua.deti.tqs.p11.application.user;

/** No account with the requested id. */
public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException() {
    super("User not found");
  }
}
