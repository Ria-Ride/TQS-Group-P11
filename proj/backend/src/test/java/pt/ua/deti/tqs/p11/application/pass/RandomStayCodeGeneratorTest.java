package pt.ua.deti.tqs.p11.application.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The codes the platform makes for stays (US14): easy to read out over a hotel desk, so no 0/O or
 * 1/I/L, and long enough that two clash only by rare luck. A seeded Random keeps the test exact.
 */
class RandomStayCodeGeneratorTest {

  private static final String PATTERN = "RIA-[A-HJ-KM-NP-Z2-9]{6}";

  @Test
  void next_isThePrefixAndSixCharactersWithoutLookAlikes() {
    RandomStayCodeGenerator generator = new RandomStayCodeGenerator(new Random(42));

    for (int i = 0; i < 500; i++) {
      assertThat(generator.next()).matches(PATTERN);
    }
  }

  @Test
  void next_givesDifferentCodes() {
    RandomStayCodeGenerator generator = new RandomStayCodeGenerator(new Random(7));
    Set<String> codes = new HashSet<>();

    for (int i = 0; i < 1000; i++) {
      codes.add(generator.next());
    }

    assertThat(codes).hasSizeGreaterThan(990);
  }

  @Test
  void next_sameSeed_sameCodes() {
    assertThat(new RandomStayCodeGenerator(new Random(1)).next())
        .isEqualTo(new RandomStayCodeGenerator(new Random(1)).next());
  }

  @Test
  void constructor_noRandomSource_isRefused() {
    assertThatThrownBy(() -> new RandomStayCodeGenerator(null))
        .isInstanceOf(NullPointerException.class);
  }
}
