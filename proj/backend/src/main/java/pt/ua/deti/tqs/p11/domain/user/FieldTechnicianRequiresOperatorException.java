package pt.ua.deti.tqs.p11.domain.user;

/**
 * BR20: only an OPERATOR can be a field technician. A specific type, so the API can map exactly
 * this rule to 400 without turning every IllegalArgumentException (a bug) into a client error.
 */
public class FieldTechnicianRequiresOperatorException extends IllegalArgumentException {

  public FieldTechnicianRequiresOperatorException() {
    super("Only an OPERATOR can be a field technician (BR20)");
  }
}
