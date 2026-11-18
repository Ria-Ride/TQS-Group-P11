package pt.ua.deti.tqs.p11.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import pt.ua.deti.tqs.p11.adapter.in.web.security.ActiveAccountAuthenticationConverter;
import pt.ua.deti.tqs.p11.adapter.in.web.security.SecurityProblemHandler;
import pt.ua.deti.tqs.p11.adapter.out.token.JwtTokenService;

/**
 * API security (US13): stateless bearer tokens and the BR23 role x endpoint rules from the access
 * matrix (src/test/resources/security/access-matrix.csv). Anything not listed is denied, so a new
 * endpoint is closed until its row is added. 401 and 403 get the same problem body as other errors.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfiguration {

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      SecurityProblemHandler problems,
      ActiveAccountAuthenticationConverter activeAccounts)
      throws Exception {
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
                // Ownership of /users/{id} is checked by @PreAuthorize (OwnershipPolicy).
                .requestMatchers(HttpMethod.GET, "/users/me", "/users/{id}")
                .authenticated()
                .requestMatchers("/admin/**")
                .hasRole("ADMIN")
                .anyRequest()
                .denyAll())
        // No token at all, or a role without permission.
        .exceptionHandling(
            errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
        // An invalid or expired token is rejected by the bearer token filter, which has its own.
        .oauth2ResourceServer(server ->
            server
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems)
                // The account (status and role) is read from the database on every request.
                .jwt(jwt -> jwt.jwtAuthenticationConverter(activeAccounts)));
    return http.build();
  }

  /** The filter validates tokens with the same key and rules as the token service. */
  @Bean
  JwtDecoder jwtDecoder(JwtTokenService tokens) {
    return tokens.jwtDecoder();
  }
}
