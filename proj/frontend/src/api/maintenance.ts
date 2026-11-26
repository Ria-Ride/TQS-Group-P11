import { requestJson } from "./client";

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

/** US10: open tickets, nearest SLA deadline first, for operators (the API answers 403 to others). */
export function listOpenTickets(token: string): Promise<Ticket[]> {
  return requestJson<Ticket[]>("/tickets", { token });
}

/** BR20: refused (409 on faultCode) when the fault already has an open ticket. */
export function openTicket(input: NewTicket, token: string): Promise<Ticket> {
  return requestJson<Ticket>("/tickets", { method: "POST", body: input, token });
}

/** BR20: refused (400 on technicianId) for anyone but an active field technician. */
export function assignTicket(
  ticketId: string,
  technicianId: string,
  token: string,
): Promise<Ticket> {
  return requestJson<Ticket>(`/tickets/${encodeURIComponent(ticketId)}/assignee`, {
    method: "PATCH",
    body: { technicianId },
    token,
  });
}

export function listTechnicians(token: string): Promise<Technician[]> {
  return requestJson<Technician[]>("/technicians", { token });
}
