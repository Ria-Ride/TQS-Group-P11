package pt.ua.deti.tqs.p11.gateway;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Gateway settings (gateway.*).
 *
 * @param timeoutDelay how long a tok_timeout charge waits before it answers; longer than the
 *     callers' read timeout, so they give up first
 */
@ConfigurationProperties("gateway")
public record GatewayProperties(@DefaultValue("PT30S") Duration timeoutDelay) {}
