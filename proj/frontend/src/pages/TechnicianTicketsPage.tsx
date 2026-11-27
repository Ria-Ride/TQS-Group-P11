import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router";
import { getProfile } from "../api/auth";
import { ApiError } from "../api/client";
import { listMyTickets, type TechnicianTicket } from "../api/maintenance";
import { clearSession, loadSession } from "../auth/session";
import { Logo } from "../components/Logo";
import { TechnicianTicketRow } from "../components/TechnicianTicketRow";
import { GENERIC_ERROR } from "./messages";

type PageState = "loading" | "forbidden" | "ready" | "error";

const NO_ACCESS = "You do not have access to this page.";

/**
 * US11: a field technician sees their open tickets, most urgent first, with where each vehicle
 * is, starts the work and closes the ticket with a record of the work done. Only an OPERATOR who
 * is a field technician gets in; hiding it from others is only for the user, the API refuses
 * someone else's ticket (BR20, BR23). After every change the list is reloaded: a closed ticket
 * leaves it.
 */
export function TechnicianTicketsPage() {
  const navigate = useNavigate();
  const [session] = useState(loadSession);
  const [state, setState] = useState<PageState>("loading");
  const [tickets, setTickets] = useState<TechnicianTicket[]>([]);

  const handleFailure = useCallback(
    (failure: unknown) => {
      if (failure instanceof ApiError && failure.status === 401) {
        clearSession(); // expired, invalid or deactivated
        navigate("/login", { replace: true });
      } else if (failure instanceof ApiError && failure.status === 403) {
        setState("forbidden");
      } else {
        setState("error");
      }
    },
    [navigate],
  );

  const refresh = useCallback(async () => {
    if (session === null) return;
    try {
      setTickets(await listMyTickets(session.accessToken));
    } catch (failure) {
      handleFailure(failure);
    }
  }, [session, handleFailure]);

  useEffect(() => {
    if (session === null) {
      return;
    }
    // Ignore a late answer if the page was left meanwhile.
    let active = true;
    async function load(token: string, userId: string) {
      try {
        const profile = await getProfile(userId, token);
        if (!active) return;
        if (profile.role !== "OPERATOR" || profile.fieldTechnician !== true) {
          setState("forbidden");
          return;
        }
        const loaded = await listMyTickets(token);
        if (!active) return;
        setTickets(loaded);
        setState("ready");
      } catch (failure: unknown) {
        if (active) handleFailure(failure);
      }
    }
    void load(session.accessToken, session.userId);
    return () => {
      active = false;
    };
  }, [session, handleFailure]);

  if (session === null) {
    return <Navigate to="/login" replace />;
  }

  const reload = () => void refresh();

  return (
    <main className="page">
      <Logo />
      <section className="card card-wide" aria-live="polite">
        <h1>My tickets</h1>
        {state === "loading" && <p>Loading your tickets…</p>}
        {state === "forbidden" && (
          <p role="alert" className="form-error">
            {NO_ACCESS}
          </p>
        )}
        {state === "error" && (
          <p role="alert" className="form-error">
            {GENERIC_ERROR}
          </p>
        )}
        {state === "ready" && (
          <>
            <h2 id="my-tickets-title" className="visually-hidden">
              My tickets
            </h2>
            {tickets.length === 0 && <p className="muted">No tickets assigned to you.</p>}
            <div className="table-scroll">
              <table className="fleet-table" aria-labelledby="my-tickets-title">
                <thead>
                  <tr>
                    <th scope="col">Vehicle</th>
                    <th scope="col">Fault</th>
                    <th scope="col">Where</th>
                    <th scope="col">Deadline</th>
                    <th scope="col">State</th>
                    <th scope="col">Work</th>
                  </tr>
                </thead>
                <tbody>
                  {tickets.map((ticket) => (
                    <TechnicianTicketRow
                      key={ticket.id}
                      ticket={ticket}
                      token={session.accessToken}
                      onChanged={reload}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
        <p className="switch">
          <Link to="/">Back to home</Link>
        </p>
      </section>
    </main>
  );
}
