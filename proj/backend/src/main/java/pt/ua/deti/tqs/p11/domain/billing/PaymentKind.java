package pt.ua.deti.tqs.p11.domain.billing;

/** What a payment is for (domain model 3.1). */
public enum PaymentKind {
  /** The price of a pass, charged at once (US03). */
  PASS,
  /** The deposit of a booking: held, then captured up to the overtime charge or released (US04). */
  DEPOSIT
}
