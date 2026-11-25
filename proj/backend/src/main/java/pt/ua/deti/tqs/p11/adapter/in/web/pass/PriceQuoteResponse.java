package pt.ua.deti.tqs.p11.adapter.in.web.pass;

import java.util.UUID;
import pt.ua.deti.tqs.p11.application.pass.PriceQuote;
import pt.ua.deti.tqs.p11.domain.billing.Money;

/** What a pass would cost the caller now, before paying (BR9). Amounts are strings ("9.00"). */
record PriceQuoteResponse(
    UUID passTypeId, String basePrice, String price, String currency, int discountPercent) {

  static PriceQuoteResponse from(PriceQuote quote) {
    return new PriceQuoteResponse(
        quote.passTypeId(),
        quote.basePrice().toString(),
        quote.price().toString(),
        Money.CURRENCY,
        quote.discountPercent());
  }
}
