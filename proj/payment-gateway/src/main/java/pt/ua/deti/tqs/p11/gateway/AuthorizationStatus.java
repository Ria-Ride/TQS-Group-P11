package pt.ua.deti.tqs.p11.gateway;

/** Where a deposit stands. */
public enum AuthorizationStatus {
  /** The amount is held on the card. */
  AUTHORIZED,
  /** Nothing was held. */
  DECLINED,
  /** Part or all of the amount was taken; the rest was given back at the same time. */
  CAPTURED,
  /** The whole amount was given back. */
  RELEASED
}