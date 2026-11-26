package pt.ua.deti.tqs.p11.application.maintenance;

import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;

/** BR20: the vehicle already has an open ticket for this fault. */
public class FaultAlreadyCoveredException extends RuntimeException {

  private final UUID vehicleId;
  private final FaultCode faultCode;

  public FaultAlreadyCoveredException(UUID vehicleId, FaultCode faultCode) {
    super(
        "Vehicle %s already has an open ticket for fault %s"
            .formatted(vehicleId, faultCode.value()));
    this.vehicleId = vehicleId;
    this.faultCode = faultCode;
  }

  public UUID vehicleId() {
    return vehicleId;
  }

  public FaultCode faultCode() {
    return faultCode;
  }
}
