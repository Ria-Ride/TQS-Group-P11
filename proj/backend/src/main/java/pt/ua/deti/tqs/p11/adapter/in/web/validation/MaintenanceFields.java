package pt.ua.deti.tqs.p11.adapter.in.web.validation;

import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;

/**
 * Format rules for maintenance fields (US10, US11). The fault code rule is the domain's own, so the
 * API and the domain can never disagree; the description and the work done fit their columns.
 */
public final class MaintenanceFields {

  public static final String FAULT_CODE = FaultCode.FORMAT;
  public static final String FAULT_CODE_MESSAGE =
      "must be UPPER_SNAKE_CASE, start with a letter and have up to 64 characters";
  public static final int DESCRIPTION_MAX = 500;

  /** US11: the work done when closing a ticket fits its column (V13). */
  public static final int WORK_DONE_MAX = 1000;

  private MaintenanceFields() {}
}
