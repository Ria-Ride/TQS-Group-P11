package pt.ua.deti.tqs.p11.domain.user;

import java.util.Locale;
import java.util.Objects;

/** An e-mail address, normalized so the same address cannot register twice. */
public record EmailAddress(String value) {

  public EmailAddress {
    value = Objects.requireNonNull(value, "value").strip().toLowerCase(Locale.ROOT);
  }
}
