package pt.ua.deti.tqs.p11.adapter.in.web;

/**
 * A request field is wrong in a way bean validation cannot express on its own (e.g. it depends on
 * another field). Reported as 400 on that field, like the other validation errors.
 */
public class InvalidFieldException extends RuntimeException {

  private final String field;

  public InvalidFieldException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
