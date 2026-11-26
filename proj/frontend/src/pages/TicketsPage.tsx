import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router";
import { getProfile } from "../api/auth";
import { ApiError } from "../api/client";
import { listVehicles, type Vehicle } from "../api/fleet";
import {
  listOpenTickets,
  listTechnicians,
  type Technician,
  type Ticket,
} from "../api/maintenance";
import { clearSession, loadSession } from "../auth/session";
import { Logo } from "../components/Logo";
import { NewTicketForm } from "../components/NewTicketForm";
import { TicketRow } from "../components/TicketRow";
import { GENERIC_ERROR } from "./messages";

type PageState = "loading" | "forbidden" | "ready" | "error";

const NO_ACCESS = "You do not have access to this page.";
const MAINTENANCE_ROLES = ["OPERATOR", "ADMIN"];

interface Lists {
  tickets: Ticket[];
  vehicles: Vehicle[];
  technicians: Technician[];
}

/** Tickets, vehicles (for their codes) and technicians, loaded together. */
async function loadLists(token: string): Promise<Lists> {
  const [tickets, vehicles, technicians] = await Promise.all([
    listOpenTickets(token),
    listVehicles(token),
    listTechnicians(token),
  ]);
  return { tickets, vehicles, technicians };
}

/**
 * US10: the operator sees the open maintenance tickets, most urgent first, opens one by hand for a
 * fault the simulator cannot detect, and assigns field technicians. Hiding it from other roles is
 * only for the user; the API itself refuses them (BR23). After every change the lists are
 * reloaded, because a ticket also changes its vehicle (BR18).
 */
export function TicketsPage() {
  const navigate = useNavigate();
  const [session] = useState(loadSession);
  const [state, setState] = useState<PageState>("loading");
  const [lists, setLists] = useState<Lists>({ tickets: [], vehicles: [], technicians: [] });

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
      setLists(await loadLists(session.accessToken));
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
        if (!MAINTENANCE_ROLES.includes(profile.role)) {
          setState("forbidden");
          return;
        }
        const loaded = await loadLists(token);
        if (!active) return;
        setLists(loaded);
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
  const codeOf = (vehicleId: string) =>
    lists.vehicles.find((vehicle) => vehicle.id === vehicleId)?.code ?? vehicleId;

  return (
    <main className="page">
      <Logo />
      <section className="card card-wide" aria-live="polite">
        <h1>Maintenance</h1>
        {state === "loading" && <p>Loading the tickets…</p>}
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
            <h2 id="tickets-title">Open tickets</h2>
            {lists.tickets.length === 0 && <p className="muted">No open tickets.</p>}
            {lists.technicians.length === 0 && <p className="muted">No field technicians yet.</p>}
            <div className="table-scroll">
              <table className="fleet-table" aria-labelledby="tickets-title">
                <thead>
                  <tr>
                    <th scope="col">Vehicle</th>
                    <th scope="col">Fault</th>
                    <th scope="col">Opened by</th>
                    <th scope="col">State</th>
                    <th scope="col">Deadline</th>
                    <th scope="col">Technician</th>
                    <th scope="col">Assign</th>
                  </tr>
                </thead>
                <tbody>
                  {lists.tickets.map((ticket) => (
                    <TicketRow
                      key={ticket.id}
                      ticket={ticket}
                      vehicleCode={codeOf(ticket.vehicleId)}
                      technicians={lists.technicians}
                      token={session.accessToken}
                      onChanged={reload}
                    />
                  ))}
                </tbody>
              </table>
            </div>
            <NewTicketForm
              vehicles={lists.vehicles}
              token={session.accessToken}
              onCreated={reload}
            />
          </>
        )}
        <p className="switch">
          <Link to="/">Back to home</Link>
        </p>
      </section>
    </main>
  );
}
