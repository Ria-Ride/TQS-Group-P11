package pt.ua.deti.tqs.p11.gateway;

import java.util.Arrays;
import java.util.Optional;

/** The test cards the gateway knows. Any other token is declined as invalid. */
enum TestCard {
  APPROVE("tok_approve"),
  DECLINE("tok_decline"),
  /** Declined, but only after the configured delay, so the caller times out first. */
  TIMEOUT("tok_timeout");

  private final String token;

  TestCard(String token) {
    this.token = token;
  }

  static Optional<TestCard> of(String token) {
    return Arrays.stream(values()).filter(card -> card.token.equals(token)).findFirst();
  }
}
