package pt.ua.deti.tqs.p11.application.fleet;

import java.util.UUID;

/**
 * Port: whether a vehicle has an open maintenance ticket (BR16: activation needs none). Owned by
 * Fleet and implemented for Maintenance (US10), so Fleet does not depend on Maintenance.
 */
public interface OpenTickets {

  boolean existFor(UUID vehicleId);
}
