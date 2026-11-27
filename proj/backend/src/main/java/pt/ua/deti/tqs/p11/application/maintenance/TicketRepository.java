package pt.ua.deti.tqs.p11.application.maintenance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.ua.deti.tqs.p11.domain.maintenance.FaultCode;
import pt.ua.deti.tqs.p11.domain.maintenance.MaintenanceTicket;

/** Port: where maintenance tickets are stored (US10). */
public interface TicketRepository {

  /**
   * Inserts or updates the ticket.
   *
   * @throws FaultAlreadyCoveredException if another open ticket exists for the same vehicle and
   *     fault (BR20); checked by the database, so it also holds for two readings stored together
   */
  MaintenanceTicket save(MaintenanceTicket ticket);

  Optional<MaintenanceTicket> findById(UUID id);

  /** BR20: the open ticket (Open, Assigned or InProgress) for this vehicle and fault, if any. */
  Optional<MaintenanceTicket> findOpenFor(UUID vehicleId, FaultCode faultCode);

  /** Every open ticket (Open, Assigned or InProgress), the nearest SLA deadline first (BR19). */
  List<MaintenanceTicket> findOpen();

  /** BR16, BR20: whether the vehicle has any open ticket, whatever its fault. */
  boolean existsOpenFor(UUID vehicleId);
}
