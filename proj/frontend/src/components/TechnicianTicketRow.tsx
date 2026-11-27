import { useState } from "react";
import { ApiError } from "../api/client";
import { closeTicket, startTicket, type TechnicianTicket } from "../api/maintenance";
import { GENERIC_ERROR } from "../pages/messages";
import {
  CANNOT_START,
  NOT_STARTED,
  NOT_THEIR_TICKET,
  TICKET_STATE_LABELS,
  WORK_DONE_MAX,
  WORK_DONE_MESSAGE,
} from "../pages/ticketLabels";

interface TechnicianTicketRowProps {
  ticket: TechnicianTicket;
  token: string;
  onChanged: () => void;
}

/** "hub-03 · Canal Central" at a hub, otherwise the last known position. */
function whereIs(ticket: TechnicianTicket): string {
  if (ticket.hubCode !== null) {
    return `${ticket.hubCode} · ${ticket.hubName}`;
  }
  if (ticket.latitude !== null && ticket.longitude !== null) {
    return `${ticket.latitude}, ${ticket.longitude}`;
  }
  return "Unknown";
}

/**
 * One of the field technician's tickets (US11): Start while Assigned, then Close with the work done
 * while InProgress (BR20). A late ticket is marked "Late" (BR19). The server stays the authority:
 * it refuses another technician's ticket and a blank work done.
 */
export function TechnicianTicketRow({ ticket, token, onChanged }: TechnicianTicketRowProps) {
  const [workDone, setWorkDone] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const workId = `work-done-${ticket.id}`;

  async function run(action: () => Promise<unknown>, onConflict: string, onInvalid: string) {
    setBusy(true);
    setError(null);
    try {
      await action();
      onChanged();
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 403) {
        setError(NOT_THEIR_TICKET);
      } else if (failure instanceof ApiError && failure.status === 409) {
        setError(onConflict);
      } else if (failure instanceof ApiError && failure.status === 400) {
        setError(onInvalid);
      } else {
        setError(GENERIC_ERROR);
      }
    } finally {
      setBusy(false);
    }
  }

  const start = () => run(() => startTicket(ticket.id, token), CANNOT_START, GENERIC_ERROR);
  const close = () =>
    run(() => closeTicket(ticket.id, workDone, token), NOT_STARTED, WORK_DONE_MESSAGE);

  return (
    <tr className={ticket.breached ? "late" : undefined}>
      <td>{ticket.vehicleCode}</td>
      <td>
        <strong>{ticket.faultCode}</strong>
        {ticket.description && <p className="muted">{ticket.description}</p>}
      </td>
      <td>{whereIs(ticket)}</td>
      <td>
        <time dateTime={ticket.slaDeadline}>{new Date(ticket.slaDeadline).toLocaleString()}</time>
        {ticket.breached && <span className="late-label">Late</span>}
      </td>
      <td>{TICKET_STATE_LABELS[ticket.state]}</td>
      <td>
        {ticket.state === "ASSIGNED" && (
          <button
            type="button"
            className="button button-small"
            disabled={busy}
            onClick={() => void start()}
          >
            Start
          </button>
        )}
        {ticket.state === "IN_PROGRESS" && (
          <span className="row-actions">
            <label htmlFor={workId} className="visually-hidden">
              Work done
            </label>
            <textarea
              id={workId}
              value={workDone}
              maxLength={WORK_DONE_MAX}
              rows={2}
              placeholder="Work done"
              onChange={(event) => setWorkDone(event.target.value)}
            />
            <button
              type="button"
              className="button button-small"
              disabled={busy || workDone.trim() === ""}
              onClick={() => void close()}
            >
              Close
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
