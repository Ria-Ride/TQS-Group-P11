package pt.ua.deti.tqs.p11.application.pass;

/** Another pass type already has this code. */
public class PassTypeCodeAlreadyInUseException extends RuntimeException {

  public PassTypeCodeAlreadyInUseException(String code) {
    super("The pass type code %s is already in use".formatted(code));
  }
}
