import type { TicketState } from "../api/maintenance";

export const TICKET_STATE_LABELS: Record<TicketState, string> = {
  OPEN: "Open",
  ASSIGNED: "Assigned",
  IN_PROGRESS: "In progress",
  CLOSED: "Closed",
  CANCELLED: "Cancelled",
};

/** BR20: a ticket can be (re)assigned until the work starts. */
export const ASSIGNABLE_STATES: TicketState[] = ["OPEN", "ASSIGNED"];

export const FAULT_CODE_MESSAGE = "Use capital letters, digits and _, starting with a letter.";
export const DESCRIPTION_MESSAGE = "Describe what you saw.";
export const VEHICLE_MESSAGE = "Choose a vehicle.";
export const FAULT_COVERED = "This fault already has an open ticket.";
export const VEHICLE_RETIRED = "This vehicle is retired.";
export const NOT_A_TECHNICIAN = "Choose an active field technician.";
export const WORK_STARTED = "Work on this ticket has started; it keeps its technician.";

// US11: the field technician's page.
export const NOT_THEIR_TICKET = "This ticket is assigned to another technician.";
export const CANNOT_START = "This ticket cannot be started now.";
export const WORK_DONE_MESSAGE = "Describe the work done.";
export const NOT_STARTED = "Start the work before closing the ticket.";
export const WORK_DONE_MAX = 1000;
