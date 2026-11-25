package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import pt.ua.deti.tqs.p11.application.pass.BuyPassCommand;

/** Body of POST /passes. The account is the caller's, from the access token, never the body. */
record BuyPassRequest(
    @Schema(description = "Id of the pass type, from GET /pass-types") @NotNull UUID passTypeId,
    @Schema(
            description = "Code of the tourist's stay at a partner hotel; omit for the base price",
            example = "RIA-DEMO-01")
        @Size(max = 32)
        String stayCode,
    @Schema(
            description =
                "What the tourist pays with. In the simulated gateway, a test card: tok_approve,"
                    + " tok_decline or tok_timeout",
            example = "tok_approve")
        @NotBlank
        @Size(max = 64)
        String paymentToken) {

  BuyPassCommand toCommand(UUID accountId) {
    return new BuyPassCommand(accountId, passTypeId, stayCode, paymentToken);
  }
}
