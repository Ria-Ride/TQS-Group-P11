package pt.ua.deti.tqs.p11.application.pass;

import java.util.Locale;
import java.util.Optional;

/** Stay codes as typed by tourists, turned into the stored form (trimmed, upper-case). */
final class StayCodes {

  private StayCodes() {}

  /** The stored form of the code, or empty when none was typed (null or blank). */
  static Optional<String> normalize(String typed) {
    return Optional.ofNullable(typed)
        .map(String::strip)
        .filter(code -> !code.isEmpty())
        .map(code -> code.toUpperCase(Locale.ROOT));
  }
}
