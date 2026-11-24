package pt.ua.deti.tqs.p11.application.pass;

/** No stay has this code. The tourist can buy again without it, at the base price. */
public class UnknownStayCodeException extends RuntimeException {

  public UnknownStayCodeException(String code) {
    super("No stay with code " + code);
  }
}
