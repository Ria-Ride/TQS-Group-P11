package pt.ua.deti.tqs.p11.application.rules;

/** Someone saved the rules at the same moment: the version is taken. */
public class RulesVersionConflictException extends RuntimeException {

  public RulesVersionConflictException(int version) {
    super(
        "The rules of use changed meanwhile (version %d is taken); read them again"
            .formatted(version));
  }
}
