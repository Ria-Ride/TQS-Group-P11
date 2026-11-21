package pt.ua.deti.tqs.p11.adapter.in.web.validation;

import pt.ua.deti.tqs.p11.domain.fleet.FleetCode;

/**
 * Format rules for fleet fields, shared by every request that carries them. The code rule is the
 * domain's own, so the API and the domain can never disagree.
 */
public final class FleetFields {

  public static final String CODE = FleetCode.FORMAT;
  public static final String CODE_MESSAGE = "must have 1 to 64 letters, digits, '-' or '_'";
  public static final int NAME_MAX = 100;

  private FleetFields() {}
}
