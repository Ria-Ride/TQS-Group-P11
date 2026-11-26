const NOT_IMPLEMENTED = "Not implemented yet (KAN-719)";

export type TicketState = "OPEN" | "ASSIGNED" | "IN_PROGRESS" | "CLOSED" | "CANCELLED";

/**
 * A maintenance ticket (US10), times in UTC. description and openedBy are null for a ticket opened
 * from a fault reading (automatic); technicianId is null until the ticket is assigned.
 */
export interface Ticket {
  id: string;
  vehicleId: string;
  faultCode: string;
  description: string | null;
  state: TicketState;
  openedAt: string;
  slaDeadline: string;
  openedBy: string | null;
  technicianId: string | null;
  automatic: boolean;
}

export interface NewTicket {
  vehicleId: string;
  faultCode: string;
  description: string;
}

/** A field technician a ticket can be assigned to (BR20). */
export interface Technician {
  id: string;
  name: string;
}

export function listOpenTickets(_token: string): Promise<Ticket[]> {
  throw new Error(NOT_IMPLEMENTED);
}

export function openTicket(_input: NewTicket, _token: string): Promise<Ticket> {
  throw new Error(NOT_IMPLEMENTED);
}

export function assignTicket(
  _ticketId: string,
  _technicianId: string,
  _token: string,
): Promise<Ticket> {
  throw new Error(NOT_IMPLEMENTED);
}

export function listTechnicians(_token: string): Promise<Technician[]> {
  throw new Error(NOT_IMPLEMENTED);
}
