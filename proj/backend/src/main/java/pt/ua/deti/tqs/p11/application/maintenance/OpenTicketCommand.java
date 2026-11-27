package pt.ua.deti.tqs.p11.application.maintenance;

import java.util.UUID;

/** An operator opens a ticket for a fault of a vehicle (US10). operatorId is the caller. */
public record OpenTicketCommand(
    UUID vehicleId, String faultCode, String description, UUID operatorId) {}
