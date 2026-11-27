package pt.ua.deti.tqs.p11.application.pass;

/** Another partner hotel already has this name. */
public class AccommodationNameAlreadyInUseException extends RuntimeException {

  public AccommodationNameAlreadyInUseException(String name) {
    super("The partner hotel %s already exists".formatted(name));
  }
}
