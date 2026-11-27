package pt.ua.deti.tqs.p11.application.maintenance;

import java.util.UUID;

public class TicketNotFoundException extends RuntimeException {

  public TicketNotFoundException(UUID id) {
    super("Maintenance ticket %s not found".formatted(id));
  }
}
