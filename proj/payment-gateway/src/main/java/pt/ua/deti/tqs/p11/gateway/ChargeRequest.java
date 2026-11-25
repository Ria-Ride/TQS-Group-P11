package pt.ua.deti.tqs.p11.gateway;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A request to take money.
 *
 * @param idempotencyKey chosen by the caller; repeating a request with the same key returns the
 *     first answer instead of charging again
 * @param token the test card (tok_approve, tok_decline, tok_timeout)
 */
public record ChargeRequest(
    @NotBlank @Size(max = 64) String idempotencyKey,
    @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount,
    @NotNull @Pattern(regexp = "EUR", message = "only EUR is accepted") String currency,
    @NotNull @Size(max = 64) String token) {}
