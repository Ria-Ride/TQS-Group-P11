import { useState } from "react";
import { ApiError } from "../api/client";
import { assignTicket, type Technician, type Ticket } from "../api/maintenance";
import { GENERIC_ERROR } from "../pages/messages";
import {
  ASSIGNABLE_STATES,
  NOT_A_TECHNICIAN,
  TICKET_STATE_LABELS,
  WORK_STARTED,
} from "../pages/ticketLabels";

interface TicketRowProps {
  ticket: Ticket;
  /** The code of the ticket's vehicle; its id when the vehicle is not in the fleet list. */
  vehicleCode: string;
  technicians: Technician[];
  token: string;
  onChanged: () => void;
}

/**
 * One open ticket in the operator's table (US10), with the choice of a field technician while the
 * work has not started (BR20). The server stays the authority: it refuses anyone else.
 */
export function TicketRow({ ticket, vehicleCode, technicians, token, onChanged }: TicketRowProps) {
  const [chosen, setChosen] = useState(ticket.technicianId ?? "");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const technician = technicians.find((candidate) => candidate.id === ticket.technicianId);
  const assignable = ASSIGNABLE_STATES.includes(ticket.state);

  async function assign() {
    setBusy(true);
    setError(null);
    try {
      await assignTicket(ticket.id, chosen, token);
      onChanged();
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 400) {
        setError(NOT_A_TECHNICIAN);
      } else if (failure instanceof ApiError && failure.status === 409) {
        setError(WORK_STARTED);
      } else {
        setError(GENERIC_ERROR);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <tr>
      <td>{vehicleCode}</td>
      <td>
        <strong>{ticket.faultCode}</strong>
        {ticket.description && <p className="muted">{ticket.description}</p>}
      </td>
      <td>{ticket.automatic ? "Telemetry" : "Operator"}</td>
      <td>{TICKET_STATE_LABELS[ticket.state]}</td>
      <td>
        <time dateTime={ticket.slaDeadline}>{new Date(ticket.slaDeadline).toLocaleString()}</time>
      </td>
      <td>{technician?.name ?? (ticket.technicianId === null ? "Not assigned" : "—")}</td>
      <td>
        {assignable && (
          <span className="row-actions">
            <select
              aria-label="Technician"
              value={chosen}
              disabled={busy || technicians.length === 0}
              onChange={(event) => setChosen(event.target.value)}
            >
              <option value="">Choose a technician</option>
              {technicians.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.name}
                </option>
              ))}
            </select>
            <button
              type="button"
              className="button button-small"
              disabled={busy || chosen === "" || chosen === ticket.technicianId}
              onClick={() => void assign()}
            >
              Assign
            </button>
          </span>
        )}
        {error && (
          <p role="alert" className="field-error">
            {error}
          </p>
        )}
      </td>
    </tr>
  );
}
