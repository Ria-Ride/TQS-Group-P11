package pt.ua.deti.tqs.p11.application.maintenance;

import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;

/**
 * BR20: the vehicle already has an open ticket for this fault. existingTicketId is that ticket,
 * when known, so the operator can be sent to it.
 */
public class FaultAlreadyCoveredException extends RuntimeException {

  private final UUID vehicleId;
  private final FaultCode faultCode;
  private final UUID existingTicketId;

  public FaultAlreadyCoveredException(UUID vehicleId, FaultCode faultCode) {
    this(vehicleId, faultCode, null);
  }

  public FaultAlreadyCoveredException(UUID vehicleId, FaultCode faultCode, UUID existingTicketId) {
    super(
        "Vehicle %s already has an open ticket for fault %s"
            .formatted(vehicleId, faultCode.value()));
    this.vehicleId = vehicleId;
    this.faultCode = faultCode;
    this.existingTicketId = existingTicketId;
  }

  public UUID vehicleId() {
    return vehicleId;
  }

  public FaultCode faultCode() {
    return faultCode;
  }

  public Optional<UUID> existingTicketId() {
    return Optional.ofNullable(existingTicketId);
  }
}
