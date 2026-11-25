package pt.ua.deti.tqs.p11.domain.billing;

/**
 * Where a payment is in its lifecycle (domain model 3.3). A pass is captured at once or declined;
 * deposits (US04) add AUTHORIZED and RELEASED.
 */
public enum PaymentState {
  /** The money was taken. */
  CAPTURED,
  /** The gateway refused, or gave no answer in time: no money was taken. */
  DECLINED
}
