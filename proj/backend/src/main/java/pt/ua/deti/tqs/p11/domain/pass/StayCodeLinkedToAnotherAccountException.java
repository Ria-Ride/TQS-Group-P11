package pt.ua.deti.tqs.p11.domain.pass;

/** BR9: the stay code already belongs to another account. Which one is not said. */
public class StayCodeLinkedToAnotherAccountException extends RuntimeException {

  public StayCodeLinkedToAnotherAccountException(String code) {
    super("Stay code %s is linked to another account".formatted(code));
  }
}
