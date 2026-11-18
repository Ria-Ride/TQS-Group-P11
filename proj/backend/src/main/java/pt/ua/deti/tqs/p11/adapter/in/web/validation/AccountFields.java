package pt.ua.deti.tqs.p11.adapter.in.web.validation;

/**
 * Format rules for account fields, shared by every request that carries them (guest registration,
 * account creation by an admin), so the two can never drift apart.
 */
public final class AccountFields {

  public static final int NAME_MAX = 100;
  public static final int PASSWORD_MIN = 8;

  /** BCrypt only uses the first 72 bytes of a password (not characters). */
  public static final int PASSWORD_MAX_BYTES = 72;

  /** {@code @Email} accepts "ana@localhost"; we also require a dot in the domain and no spaces. */
  public static final String DOTTED_DOMAIN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

  private AccountFields() {}
}
