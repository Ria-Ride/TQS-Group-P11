package pt.ua.deti.tqs.p11.domain.maintenance;

import java.util.UUID;

/** BR20: a ticket can only be assigned to an active OPERATOR who is a field technician. */
public class NotAFieldTechnicianException extends RuntimeException {

  private final UUID userId;

  public NotAFieldTechnicianException(UUID userId) {
    super("Account %s is not an active field technician".formatted(userId));
    this.userId = userId;
  }

  public UUID userId() {
    return userId;
  }
}
