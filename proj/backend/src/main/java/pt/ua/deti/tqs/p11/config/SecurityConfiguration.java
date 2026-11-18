package pt.ua.deti.tqs.p11.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import pt.ua.deti.tqs.p11.adapter.out.token.JwtTokenService;

/**
 * Minimal API security for US01: stateless bearer tokens, public auth endpoints, everything else
 * authenticated. US13 extends it with the role x endpoint matrix and consistent 401/403 bodies.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfiguration {

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    http
        // Tokens travel in a header, never in a cookie, so CSRF does not apply.
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests ->
            requests
                .requestMatchers(HttpMethod.POST, "/auth/guest", "/auth/login")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health")
                .permitAll()
                // Errors such as malformed JSON are forwarded to /error; keep their status.
                .requestMatchers("/error")
                .permitAll()
                .anyRequest()
                .authenticated())
        .oauth2ResourceServer(
            server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(roleClaimConverter())));
    return http.build();
  }

  /** The filter validates tokens with the same key and rules as the token service. */
  @Bean
  JwtDecoder jwtDecoder(JwtTokenService tokens) {
    return tokens.jwtDecoder();
  }

  /** Turns the "role" claim into a Spring authority, e.g. END_USER becomes ROLE_END_USER. */
  private static JwtAuthenticationConverter roleClaimConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName(JwtTokenService.ROLE_CLAIM);
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }
}
