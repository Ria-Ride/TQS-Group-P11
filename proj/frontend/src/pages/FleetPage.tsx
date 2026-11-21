import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router";
import { getProfile } from "../api/auth";
import { ApiError } from "../api/client";
import { listHubs, listVehicles, type Hub, type Vehicle } from "../api/fleet";
import { clearSession, loadSession } from "../auth/session";
import { HubRow } from "../components/HubRow";
import { Logo } from "../components/Logo";
import { NewHubForm } from "../components/NewHubForm";
import { NewVehicleForm } from "../components/NewVehicleForm";
import { VehicleRow } from "../components/VehicleRow";
import { GENERIC_ERROR } from "./messages";

type PageState = "loading" | "forbidden" | "ready" | "error";

const NO_ACCESS = "You do not have access to this page.";
const FLEET_ROLES = ["OPERATOR", "ADMIN"];

/**
 * US08: the operator registers hubs and vehicles and manages the vehicle lifecycle. Hiding it from
 * other roles is only for the user; the API itself refuses them (BR23). After every change both
 * lists are reloaded, because one change can affect both (a move frees a slot in a hub).
 */
export function FleetPage() {
  const navigate = useNavigate();
  const [session] = useState(loadSession);
  const [state, setState] = useState<PageState>("loading");
  const [hubs, setHubs] = useState<Hub[]>([]);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);

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
      const [loadedHubs, loadedVehicles] = await Promise.all([
        listHubs(session.accessToken),
        listVehicles(session.accessToken),
      ]);
      setHubs(loadedHubs);
      setVehicles(loadedVehicles);
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
        if (!FLEET_ROLES.includes(profile.role)) {
          setState("forbidden");
          return;
        }
        const [loadedHubs, loadedVehicles] = await Promise.all([
          listHubs(token),
          listVehicles(token),
        ]);
        if (!active) return;
        setHubs(loadedHubs);
        setVehicles(loadedVehicles);
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
        <h1>Fleet</h1>
        {state === "loading" && <p>Loading the fleet…</p>}
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
            <h2 id="hubs-title">Hubs</h2>
            {hubs.length === 0 && <p className="muted">No hubs yet.</p>}
            <div className="table-scroll">
              <table className="fleet-table" aria-labelledby="hubs-title">
                <thead>
                  <tr>
                    <th scope="col">Code</th>
                    <th scope="col">Name</th>
                    <th scope="col">Vehicles</th>
                    <th scope="col">Capacity</th>
                  </tr>
                </thead>
                <tbody>
                  {hubs.map((hub) => (
                    <HubRow key={hub.id} hub={hub} token={session.accessToken} onChanged={reload} />
                  ))}
                </tbody>
              </table>
            </div>
            <NewHubForm token={session.accessToken} onCreated={reload} />

            <h2 id="vehicles-title">Vehicles</h2>
            {vehicles.length === 0 && <p className="muted">No vehicles yet.</p>}
            <div className="table-scroll">
              <table className="fleet-table" aria-labelledby="vehicles-title">
                <thead>
                  <tr>
                    <th scope="col">Code</th>
                    <th scope="col">Type</th>
                    <th scope="col">State</th>
                    <th scope="col">Hub</th>
                    <th scope="col">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {vehicles.map((vehicle) => (
                    <VehicleRow
                      key={vehicle.id}
                      vehicle={vehicle}
                      hubs={hubs}
                      token={session.accessToken}
                      onChanged={reload}
                    />
                  ))}
                </tbody>
              </table>
            </div>
            <NewVehicleForm hubs={hubs} token={session.accessToken} onCreated={reload} />
          </>
        )}
        <p className="switch">
          <Link to="/">Back to home</Link>
        </p>
      </section>
    </main>
  );
}
