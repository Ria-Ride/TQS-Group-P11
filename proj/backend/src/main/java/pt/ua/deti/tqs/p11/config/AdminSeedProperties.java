package pt.ua.deti.tqs.p11.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Initial ADMIN of the environment (US13). Filled from the ADMIN_NAME, ADMIN_EMAIL and
 * ADMIN_PASSWORD environment variables (see application.properties); never committed.
 */
@ConfigurationProperties("app.admin")
public record AdminSeedProperties(String name, String email, String password) {

  /** Both are needed to create the account. */
  boolean isConfigured() {
    return StringUtils.hasText(email) && StringUtils.hasText(password);
  }
}
