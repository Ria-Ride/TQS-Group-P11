package pt.ua.deti.tqs.p11.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Telemetry from the fleet simulator over MQTT (US09), set with APP_TELEMETRY_ENABLED,
 * APP_TELEMETRY_HOST, APP_TELEMETRY_PORT, APP_TELEMETRY_USERNAME, APP_TELEMETRY_PASSWORD,
 * APP_TELEMETRY_CLIENT_ID and APP_TELEMETRY_FLEET_ID. Off by default: Docker Compose turns it on.
 *
 * @param username empty to connect without authentication
 * @param clientId empty to generate a unique one at start-up
 * @param fleetId the only fleet the platform listens to (simulator contract identifier)
 * @param commandTtl how long a command to a vehicle stays valid after it is sent (its expiresAt)
 */
@ConfigurationProperties("app.telemetry")
public record TelemetryProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("localhost") String host,
    @DefaultValue("1883") int port,
    @DefaultValue("") String username,
    @DefaultValue("") String password,
    @DefaultValue("") String clientId,
    @DefaultValue("fleet-01") String fleetId,
    @DefaultValue("PT30S") Duration commandTtl) {}
