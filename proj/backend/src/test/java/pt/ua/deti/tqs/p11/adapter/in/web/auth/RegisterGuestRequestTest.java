package pt.ua.deti.tqs.p11.adapter.in.web.auth;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RegisterGuestRequestTest {
  private static final String NAME = "Ana Sousa";
  private static final String EMAIL = "ana.sousa@example.com";
  private static final String PASSWORD = "Passw0rd!";

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeFactory() {
    factory.close();
  }

  private static Set<String> invalidFields(RegisterGuestRequest request) {
    return validator.validate(request).stream()
        .map(violation -> violation.getPropertyPath().toString())
        .collect(toSet());
  }

  @Test
  void validRequest_hasNoViolations() {
    assertThat(invalidFields(new RegisterGuestRequest(NAME, EMAIL, PASSWORD))).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void name_missingOrBlank_isInvalid(String name) {
    assertThat(invalidFields(new RegisterGuestRequest(name, EMAIL, PASSWORD)))
        .containsExactly("name");
  }

  @Test
  void name_with100Characters_isValid() {
    assertThat(invalidFields(new RegisterGuestRequest("a".repeat(100), EMAIL, PASSWORD))).isEmpty();
  }

  @Test
  void name_with101Characters_isInvalid() {
    assertThat(invalidFields(new RegisterGuestRequest("a".repeat(101), EMAIL, PASSWORD)))
        .containsExactly("name");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "ana.sousa",
        "ana.sousa@",
        "@example.com",
        "ana sousa@example.com",
        "ana.sousa@localhost",
        "ana.sousa@example."
      })
  void email_invalid_isRefused(String email) {
    assertThat(invalidFields(new RegisterGuestRequest(NAME, email, PASSWORD)))
        .containsExactly("email");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ana.sousa@example.com", "Ana.Sousa+ride@mail.example.pt"})
  void email_valid_isAccepted(String email) {
    assertThat(invalidFields(new RegisterGuestRequest(NAME, email, PASSWORD))).isEmpty();
  }

  @Test
  void password_missing_isInvalid() {
    assertThat(invalidFields(new RegisterGuestRequest(NAME, EMAIL, null)))
        .containsExactly("password");
  }

  @ParameterizedTest
  @ValueSource(ints = {7, 73})
  void password_outsideLengthLimits_isInvalid(int length) {
    assertThat(invalidFields(new RegisterGuestRequest(NAME, EMAIL, "x".repeat(length))))
        .containsExactly("password");
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 72})
  void password_atLengthLimits_isValid(int length) {
    assertThat(invalidFields(new RegisterGuestRequest(NAME, EMAIL, "x".repeat(length)))).isEmpty();
  }
}