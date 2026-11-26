package pt.ua.deti.tqs.p11.gateway;

/** No deposit was authorized with this key. */
public class AuthorizationNotFoundException extends RuntimeException {

  public AuthorizationNotFoundException(String key) {
    super("No authorization with key " + key);
  }
}