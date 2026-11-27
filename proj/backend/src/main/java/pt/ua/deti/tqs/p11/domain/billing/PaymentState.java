package pt.ua.deti.tqs.p11.domain.billing;

/**
 * Where a payment is in its lifecycle (domain model 3.3). A pass is captured at once or declined; a
 * deposit is authorized, then released (or, from US06, captured), or declined.
 */
public enum PaymentState {
  /** A deposit is held on the card. */
  AUTHORIZED,
  /** The money was taken. */
  CAPTURED,
  /** A held deposit was given back whole. */
  RELEASED,
  /** The gateway refused, or gave no answer in time: no money was taken or held. */
  DECLINED
}
