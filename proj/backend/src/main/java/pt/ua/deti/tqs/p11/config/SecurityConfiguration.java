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
import pt.ua.deti.tqs.p11.domain.user.Role;

/**
 * API security (US13): stateless bearer tokens and the BR23 role x endpoint rules. They mirror the
 * access matrix (src/test/resources/security/access-matrix.csv); AccessMatrixApiIT fails when the
 * two disagree, so change both together. ADMIN is allowed everywhere. Anything not listed is
 * denied. 401 and 403 get the same problem body as other errors.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfiguration {

  private static final String END_USER = Role.END_USER.name();
  private static final String OPERATOR = Role.OPERATOR.name();
  private static final String CLIENT_MANAGER = Role.CLIENT_MANAGER.name();
  private static final String ADMIN = Role.ADMIN.name();

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
                // Public: guest registration, login, health.
                .requestMatchers(HttpMethod.POST, "/auth/guest", "/auth/login")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health")
                .permitAll()
                // API documentation (OpenAPI description and Swagger UI): read only, no data.
                .requestMatchers(
                    HttpMethod.GET,
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**")
                .permitAll()
                // Errors such as malformed JSON are forwarded to /error; keep their status.
                .requestMatchers("/error")
                .permitAll()
                // Users. Ownership of /users/{id} is checked by @PreAuthorize (OwnershipPolicy).
                .requestMatchers(HttpMethod.GET, "/users/me", "/users/{id}")
                .authenticated()
                // Admin account management (US13).
                .requestMatchers("/admin/**")
                .hasRole(ADMIN)
                // Catalogue every role reads: hubs, vehicles, vehicle types, rules, pass types.
                .requestMatchers(
                    HttpMethod.GET,
                    "/hubs",
                    "/hubs/{id}",
                    "/hubs/{id}/vehicles",
                    "/vehicles",
                    "/vehicles/{id}",
                    "/vehicle-types",
                    "/usage-rules",
                    "/pass-types")
                .authenticated()
                // Fleet operations and maintenance (US08, US10-US12).
                .requestMatchers(
                    HttpMethod.GET, "/vehicles/{id}/history", "/tickets", "/fleet/overview")
                .hasAnyRole(OPERATOR, ADMIN)
                .requestMatchers(
                    HttpMethod.POST,
                    "/hubs",
                    "/vehicles",
                    "/vehicles/{id}/transitions",
                    "/tickets",
                    "/tickets/{id}/close")
                .hasAnyRole(OPERATOR, ADMIN)
                .requestMatchers(HttpMethod.PATCH, "/tickets/{id}/assignee")
                .hasAnyRole(OPERATOR, ADMIN)
                // Hub capacity: operators (US08) and client managers (US14).
                .requestMatchers(HttpMethod.PATCH, "/hubs/{id}")
                .hasAnyRole(OPERATOR, CLIENT_MANAGER, ADMIN)
                // Client management and KPIs (US14, US15).
                .requestMatchers(HttpMethod.GET, "/stay-codes", "/kpis")
                .hasAnyRole(CLIENT_MANAGER, ADMIN)
                .requestMatchers(HttpMethod.POST, "/pass-types", "/stay-codes")
                .hasAnyRole(CLIENT_MANAGER, ADMIN)
                .requestMatchers(HttpMethod.PATCH, "/vehicle-types/{type}", "/pass-types/{id}")
                .hasAnyRole(CLIENT_MANAGER, ADMIN)
                .requestMatchers(HttpMethod.PUT, "/usage-rules")
                .hasAnyRole(CLIENT_MANAGER, ADMIN)
                // Tourist flows (US03-US07). Ownership of a booking is checked in its story.
                .requestMatchers(
                    HttpMethod.GET,
                    "/pass-types/{id}/price",
                    "/passes",
                    "/bookings",
                    "/bookings/{id}")
                .hasAnyRole(END_USER, ADMIN)
                .requestMatchers(
                    HttpMethod.POST,
                    "/passes",
                    "/bookings",
                    "/bookings/{id}/start",
                    "/bookings/{id}/end",
                    "/bookings/{id}/cancel")
                .hasAnyRole(END_USER, ADMIN)
                // Anything not in the matrix is closed until its row is added.
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
