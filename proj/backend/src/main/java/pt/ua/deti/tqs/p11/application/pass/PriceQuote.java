package pt.ua.deti.tqs.p11.application.pass;

import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/**
 * What a pass would cost the account now (BR9), shown before paying. Nothing is reserved: the
 * purchase checks the code again.
 *
 * @param discountPercent the hotel's discount applied, 0 without a stay code
 */
public record PriceQuote(UUID passTypeId, Money basePrice, Money price, int discountPercent) {}
