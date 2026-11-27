package pt.ua.deti.tqs.p11.adapter.in.web.pass;

/** Limits of the pass type fields in requests. */
final class PassTypeFields {

  /** A pass lasts at most a year; the domain only asks for at least one day. */
  static final int MAX_VALIDITY_DAYS = 365;

  private PassTypeFields() {}
}
