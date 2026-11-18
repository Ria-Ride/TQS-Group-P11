package pt.ua.deti.tqs.p11.adapter.out.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import pt.ua.deti.tqs.p11.application.auth.TokenClaims;
import pt.ua.deti.tqs.p11.domain.user.Role;

class JwtTokenServiceTest {

  // Test-only dummy secrets (32 bytes). Real secrets come from the environment.
  private static final String SECRET = "test-only-secret-0123456789abcde";
  private static final String OTHER_SECRET = "other-test-secret-0123456789abcd";

  private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
  private static final Duration TTL = Duration.ofHours(1);
  private static final UUID USER_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

  private static JwtTokenService serviceAt(Instant instant) {
    return serviceAt(instant, SECRET);
  }

  private static JwtTokenService serviceAt(Instant instant, String secret) {
    return new JwtTokenService(secret, TTL, Clock.fixed(instant, ZoneOffset.UTC));
  }

  private static String payloadJson(String token) {
    String payload = token.split("\\.")[1];
    return new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
  }

  @Test
  void issue_validToken_isVerifiedWithUserIdRoleAndExpiry() {
    String token = serviceAt(NOW).issue(USER_ID, Role.END_USER);

    assertThat(serviceAt(NOW).verify(token))
        .contains(new TokenClaims(USER_ID, Role.END_USER, NOW.plus(TTL)));
  }

  @Test
  void issue_payload_carriesUserIdAsSubjectAndRoleClaim() {
    String json = payloadJson(serviceAt(NOW).issue(USER_ID, Role.END_USER));

    assertThat(json).contains("\"sub\":\"" + USER_ID + "\"").contains("\"role\":\"END_USER\"");
  }

  @Test
  void verify_oneSecondBeforeExpiry_isAccepted() {
    String token = serviceAt(NOW).issue(USER_ID, Role.END_USER);

    assertThat(serviceAt(NOW.plus(TTL).minusSeconds(1)).verify(token)).isPresent();
  }

  @Test
  void verify_oneSecondAfterExpiry_isRejected() {
    String token = serviceAt(NOW).issue(USER_ID, Role.END_USER);

    assertThat(serviceAt(NOW.plus(TTL).plusSeconds(1)).verify(token)).isEmpty();
  }

  @Test
  void verify_payloadSwappedToAdmin_isRejected() {
    String[] userToken = serviceAt(NOW).issue(USER_ID, Role.END_USER).split("\\.");
    String[] adminToken = serviceAt(NOW).issue(USER_ID, Role.ADMIN).split("\\.");
    String forged = userToken[0] + "." + adminToken[1] + "." + userToken[2];

    assertThat(serviceAt(NOW).verify(forged)).isEmpty();
  }

  @Test
  void verify_tokenSignedWithAnotherSecret_isRejected() {
    String token = serviceAt(NOW, OTHER_SECRET).issue(USER_ID, Role.END_USER);

    assertThat(serviceAt(NOW).verify(token)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "not-a-token", "a.b.c"})
  void verify_malformedToken_isRejected(String token) {
    assertThat(serviceAt(NOW).verify(token)).isEmpty();
  }

  @Test
  void constructor_secretWith31Bytes_isRefused() {
    String shortSecret = SECRET.substring(1);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    assertThatThrownBy(() -> new JwtTokenService(shortSecret, TTL, clock))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_secretWith32Bytes_isAccepted() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    assertThatCode(() -> new JwtTokenService(SECRET, TTL, clock)).doesNotThrowAnyException();
  }

  @Test
  void jwtDecoder_appliesTheSameExpiryRules() {
    String token = serviceAt(NOW).issue(USER_ID, Role.END_USER);
    JwtDecoder decoderAfterExpiry = serviceAt(NOW.plus(TTL).plusSeconds(1)).jwtDecoder();

    assertThatThrownBy(() -> decoderAfterExpiry.decode(token)).isInstanceOf(JwtException.class);
  }
}
