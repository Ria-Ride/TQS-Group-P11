import { requestJson } from "./client";

export type TicketState = "OPEN" | "ASSIGNED" | "IN_PROGRESS" | "CLOSED" | "CANCELLED";

/**
 * A maintenance ticket (US10, US11), times in UTC. description and openedBy are null for a ticket
 * opened from a fault reading (automatic); technicianId is null until the ticket is assigned;
 * closedAt, workDone and timeToRepairMinutes are null until it is closed. breached (BR19) is for
 * now while open, and for the closing time once closed.
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
  closedAt: string | null;
  workDone: string | null;
  timeToRepairMinutes: number | null;
  breached: boolean;
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

/**
 * One ticket of a field technician's list (US11). hubCode and hubName are null when the vehicle is
 * at no hub; latitude and longitude are its last known position, null before the first reading.
 */
export interface TechnicianTicket {
  id: string;
  vehicleId: string;
  vehicleCode: string;
  faultCode: string;
  description: string | null;
  state: TicketState;
  openedAt: string;
  slaDeadline: string;
  hubCode: string | null;
  hubName: string | null;
  latitude: number | null;
  longitude: number | null;
  breached: boolean;
}

/** US11: the caller's open tickets, nearest SLA deadline first. */
export function listMyTickets(token: string): Promise<TechnicianTicket[]> {
  return requestJson<TechnicianTicket[]>("/tickets/mine", { token });
}

/** Refused with 403 for someone else's ticket and 409 for a ticket that is not Assigned. */
export function startTicket(ticketId: string, token: string): Promise<Ticket> {
  return requestJson<Ticket>(`/tickets/${encodeURIComponent(ticketId)}/start`, {
    method: "POST",
    body: {},
    token,
  });
}

/** BR20: refused with 400 on workDone when blank, 409 when the work has not started. */
export function closeTicket(ticketId: string, workDone: string, token: string): Promise<Ticket> {
  return requestJson<Ticket>(`/tickets/${encodeURIComponent(ticketId)}/close`, {
    method: "POST",
    body: { workDone },
    token,
  });
}
