package pt.ua.deti.tqs.p11.application.maintenance;

import java.util.UUID;

/** A technician closes a ticket, recording the work done (US11). technicianId is the caller. */
public record CloseTicketCommand(UUID ticketId, UUID technicianId, String workDone) {}
