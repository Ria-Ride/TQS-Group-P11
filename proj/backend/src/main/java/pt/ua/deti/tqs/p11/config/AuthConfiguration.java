package pt.ua.deti.tqs.p11.config;

import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.adapter.out.security.BCryptPasswordHasher;
import pt.ua.deti.tqs.p11.adapter.out.token.JwtTokenService;
import pt.ua.deti.tqs.p11.application.auth.AccessTokenService;
import pt.ua.deti.tqs.p11.application.auth.RegisterGuestUseCase;
import pt.ua.deti.tqs.p11.application.user.PasswordHasher;
import pt.ua.deti.tqs.p11.application.user.UserRepository;

/** Wires the authentication use cases to their adapters (ports and adapters). */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
class AuthConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  AccessTokenService accessTokenService(JwtProperties jwt, Clock clock) {
    return new JwtTokenService(jwt.secret(), jwt.timeToLive(), clock);
  }

  @Bean
  PasswordHasher passwordHasher() {
    return new BCryptPasswordHasher();
  }

  @Bean
  RegisterGuestUseCase registerGuestUseCase(
      UserRepository users, PasswordHasher passwordHasher, AccessTokenService tokens) {
    return new RegisterGuestUseCase(users, passwordHasher, tokens, UUID::randomUUID);
  }
}
