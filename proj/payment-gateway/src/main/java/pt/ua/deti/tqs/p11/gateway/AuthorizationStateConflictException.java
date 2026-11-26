package pt.ua.deti.tqs.p11.gateway;

/** The step does not fit where the deposit stands (e.g. capturing after a release). */
public class AuthorizationStateConflictException extends RuntimeException {

  public AuthorizationStateConflictException(
      String key, AuthorizationStatus status, String action) {
    super("The authorization %s is %s: it cannot %s".formatted(key, status, action));
  }
}