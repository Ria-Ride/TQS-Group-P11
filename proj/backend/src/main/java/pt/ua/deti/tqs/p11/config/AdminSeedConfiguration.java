package pt.ua.deti.tqs.p11.config;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.p11.application.user.PasswordHasher;
import pt.ua.deti.tqs.p11.application.user.SeedInitialAdminUseCase;
import pt.ua.deti.tqs.p11.application.user.UserRepository;

/** Creates the initial ADMIN once the application has started (US13). */
@Configuration
@EnableConfigurationProperties(AdminSeedProperties.class)
class AdminSeedConfiguration {

  private static final Logger log = LoggerFactory.getLogger(AdminSeedConfiguration.class);

  @Bean
  SeedInitialAdminUseCase seedInitialAdminUseCase(
      UserRepository users, PasswordHasher passwordHasher) {
    return new SeedInitialAdminUseCase(users, passwordHasher, UUID::randomUUID);
  }

  /** Runs once, after startup. Without ADMIN_EMAIL and ADMIN_PASSWORD it only warns. */
  @Bean
  ApplicationRunner initialAdmin(AdminSeedProperties admin, SeedInitialAdminUseCase seed) {
    return args -> {
      if (!admin.isConfigured()) {
        log.warn("No initial admin configured: set ADMIN_EMAIL and ADMIN_PASSWORD to create one");
        return;
      }
      if (seed.seed(admin.name(), admin.email(), admin.password())) {
        log.info("Initial admin account created");
      }
    };
  }
}
