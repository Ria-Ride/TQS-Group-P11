package pt.ua.deti.tqs.p11.adapter.in.web.validation;

import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;

/**
 * Format rules for maintenance fields (US10). The fault code rule is the domain's own, so the API
 * and the domain can never disagree; the description fits its column (V9).
 */
public final class MaintenanceFields {

  public static final String FAULT_CODE = FaultCode.FORMAT;
  public static final String FAULT_CODE_MESSAGE =
      "must be UPPER_SNAKE_CASE, start with a letter and have up to 64 characters";
  public static final int DESCRIPTION_MAX = 500;

  private MaintenanceFields() {}
}
