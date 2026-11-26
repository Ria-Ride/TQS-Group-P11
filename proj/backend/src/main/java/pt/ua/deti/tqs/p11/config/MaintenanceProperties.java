package pt.ua.deti.tqs.p11.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Maintenance rules read from configuration (app.maintenance.*). BR19: slaHours is the contracted
 * repair time, so a ticket is due its opening time plus slaHours. Until the Client Rules module
 * exists (US14), the client changes it here.
 */
@ConfigurationProperties("app.maintenance")
public record MaintenanceProperties(@DefaultValue("24") int slaHours) {

  /** Refuses a repair time of zero or less, so a wrong value stops the start-up. */
  public MaintenanceProperties {
    if (slaHours <= 0) {
      throw new IllegalArgumentException("app.maintenance.sla-hours must be positive: " + slaHours);
    }
  }

  public Duration sla() {
    return Duration.ofHours(slaHours);
  }
}
