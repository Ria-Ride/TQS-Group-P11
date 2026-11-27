package pt.ua.deti.tqs.p11.gateway;

import java.math.BigDecimal;

/** More was asked than the deposit holds. */
public class CaptureExceedsAuthorizationException extends RuntimeException {

  public CaptureExceedsAuthorizationException(String key, BigDecimal asked, BigDecimal held) {
    super(
        "The authorization %s holds %s; %s cannot be captured"
            .formatted(key, held.toPlainString(), asked.toPlainString()));
  }
}