package pt.ua.deti.tqs.p11.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Pass settings (app.passes.*).
 *
 * @param zone the hotel's time zone: a stay code is valid on calendar days at the hotel (BR9), so
 *     the current instant is turned into a date in this zone. Everything else stays in UTC.
 */
@ConfigurationProperties("app.passes")
public record PassProperties(@DefaultValue("Europe/Lisbon") ZoneId zone) {}
