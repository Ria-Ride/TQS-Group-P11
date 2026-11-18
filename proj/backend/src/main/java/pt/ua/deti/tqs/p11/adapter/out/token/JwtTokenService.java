package pt.ua.deti.tqs.p11.adapter.out.token;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import pt.ua.deti.tqs.p11.application.auth.AccessTokenService;
import pt.ua.deti.tqs.p11.application.auth.TokenClaims;
import pt.ua.deti.tqs.p11.domain.user.Role;

/** HS256-signed JWT access tokens: sub = user id, "role" = the user's role. */
public class JwtTokenService implements AccessTokenService {

  /** HS256 needs a key of at least 256 bits. */
  static final int MIN_SECRET_BYTES = 32;

  public static final String ROLE_CLAIM = "role";

  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final Duration timeToLive;
  private final Clock clock;

  public JwtTokenService(String secret, Duration timeToLive, Clock clock) {
    byte[] keyBytes = Objects.requireNonNull(secret, "secret").getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < MIN_SECRET_BYTES) {
      throw new IllegalArgumentException(
          "JWT secret must have at least " + MIN_SECRET_BYTES + " bytes");
    }
    SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
    this.timeToLive = Objects.requireNonNull(timeToLive, "timeToLive");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));

    NimbusJwtDecoder nimbusDecoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    // No clock skew: expiry is exact, and the injected clock keeps tests deterministic.
    JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
    timestampValidator.setClock(clock);
    nimbusDecoder.setJwtValidator(timestampValidator);
    this.decoder = nimbusDecoder;
  }

  @Override
  public String issue(UUID userId, Role role) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(Objects.requireNonNull(userId, "userId").toString())
            .claim(ROLE_CLAIM, Objects.requireNonNull(role, "role").name())
            .issuedAt(now)
            .expiresAt(now.plus(timeToLive))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  @Override
  public Optional<TokenClaims> verify(String token) {
    try {
      Jwt jwt = decoder.decode(token);
      return Optional.of(
          new TokenClaims(
              UUID.fromString(jwt.getSubject()),
              Role.valueOf(jwt.getClaimAsString(ROLE_CLAIM)),
              jwt.getExpiresAt()));
    } catch (JwtException | IllegalArgumentException | NullPointerException e) {
      // Invalid, expired or forged token, or unknown subject/role: not authenticated.
      return Optional.empty();
    }
  }

  /** Decoder with the same key and rules, used by the security filter (US13). */
  public JwtDecoder jwtDecoder() {
    return decoder;
  }
}
