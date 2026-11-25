package pt.ua.deti.tqs.p11.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the payment gateway is and how long to wait for it (app.payment.*). A charge with no answer
 * within readTimeout is treated as not paid (BR10).
 */
@ConfigurationProperties("app.payment")
public record PaymentProperties(
    @DefaultValue("http://localhost:8090") String baseUrl,
    @DefaultValue("PT2S") Duration connectTimeout,
    @DefaultValue("PT5S") Duration readTimeout) {}
