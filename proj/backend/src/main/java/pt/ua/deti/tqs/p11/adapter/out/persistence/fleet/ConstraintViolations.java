package pt.ua.deti.tqs.p11.adapter.out.persistence.fleet;

import org.springframework.dao.DataIntegrityViolationException;

/** Tells which database constraint a violation came from, by its name in the migrations. */
final class ConstraintViolations {

  private ConstraintViolations() {}

  static boolean isViolationOf(DataIntegrityViolationException e, String constraint) {
    String cause = e.getMostSpecificCause().getMessage();
    return cause != null && cause.contains(constraint);
  }
}
