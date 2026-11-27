package pt.ua.deti.tqs.p11.application.pass;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Makes stay codes such as RIA-7K3M9Q: a prefix and six characters. The alphabet leaves out 0, O,
 * 1, I and L, so a code read out loud or copied by hand is not mistaken. 31 characters in 6 places
 * give about 887 million codes; a clash is asked again by the caller.
 */
public class RandomStayCodeGenerator implements StayCodeGenerator {

  static final String PREFIX = "RIA-";
  private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  private static final int LENGTH = 6;

  private final RandomGenerator random;

  /** Pass a SecureRandom in production, so codes cannot be guessed from earlier ones. */
  public RandomStayCodeGenerator(RandomGenerator random) {
    this.random = Objects.requireNonNull(random, "random");
  }

  @Override
  public String next() {
    StringBuilder code = new StringBuilder(PREFIX);
    for (int i = 0; i < LENGTH; i++) {
      code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return code.toString();
  }
}
