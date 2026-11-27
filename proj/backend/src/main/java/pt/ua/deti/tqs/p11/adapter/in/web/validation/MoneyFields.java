package pt.ua.deti.tqs.p11.adapter.in.web.validation;

/**
 * Format of an amount in a request: euros with at most two decimals, never negative, as the
 * database holds it (NUMERIC(10, 2)). Amounts are strings so that no floating point ever touches
 * money.
 */
public final class MoneyFields {

  public static final String AMOUNT = "^\\d{1,8}(\\.\\d{1,2})?$";
  public static final String MESSAGE =
      "must be an amount in euros with at most two decimals, such as 10 or 10.50";

  private MoneyFields() {}
}
