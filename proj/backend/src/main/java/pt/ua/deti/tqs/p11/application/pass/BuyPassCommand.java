package pt.ua.deti.tqs.p11.application.pass;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A tourist buys a pass (US03).
 *
 * @param accountId the account that pays and gets the pass, from the access token
 * @param stayCode the code of the tourist's stay at a partner hotel, or null for the base price
 * @param paymentToken what the tourist pays with (a test card in the simulated gateway)
 */
public record BuyPassCommand(
    UUID accountId, UUID passTypeId, String stayCode, String paymentToken) {

  public BuyPassCommand {
    Objects.requireNonNull(accountId, "accountId");
    Objects.requireNonNull(passTypeId, "passTypeId");
    Objects.requireNonNull(paymentToken, "paymentToken");
  }

  /** The stay code as stored (trimmed, upper-case), if one was given. */
  public Optional<String> normalizedStayCode() {
    return StayCodes.normalize(stayCode);
  }
}
