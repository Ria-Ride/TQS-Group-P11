package pt.ua.deti.tqs.p11.domain.user;

/**
 * Admin accounts are managed directly in the database: through the API their role cannot be changed
 * or given to someone else, and they cannot be deactivated (US13).
 */
public class ProtectedAccountException extends RuntimeException {

  public ProtectedAccountException(String message) {
    super(message);
  }
}
