import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router";
import { ApiError } from "../api/client";
import {
  listHubs,
  searchHubVehicles,
  type Hub,
  type HubVehicle,
  type VehicleType,
} from "../api/fleet";
import { clearSession, loadSession } from "../auth/session";
import { Logo } from "../components/Logo";
import {
  hubLabel,
  RIDE_LENGTHS,
  TYPE_LABELS,
  unbookableText,
  VEHICLE_TYPES,
} from "./fleetLabels";
import { GENERIC_ERROR } from "./messages";

type PageState = "loading" | "ready" | "error";

const NO_HUBS = "There are no hubs yet.";
const NOTHING_TO_BOOK = "No vehicle is available at this hub right now.";

/**
 * US02: a tourist picks a hub, optionally a vehicle type and the length of the ride, and sees the
 * vehicles of the hub with their battery. A vehicle that cannot be booked (BR1, BR2) is listed with
 * the reason and cannot be selected. Which vehicles can be booked is decided by the API, not here.
 * Booking itself comes with US04, so selecting only marks the vehicle for now.
 */
export function SearchPage() {
  const navigate = useNavigate();
  const [session] = useState(loadSession);
  const [state, setState] = useState<PageState>("loading");
  const [hubs, setHubs] = useState<Hub[]>([]);
  const [hubId, setHubId] = useState("");
  const [type, setType] = useState<VehicleType | null>(null);
  const [minutes, setMinutes] = useState<number | null>(null);
  const [vehicles, setVehicles] = useState<HubVehicle[]>([]);
  const [selected, setSelected] = useState<string | null>(null);

  const token = session?.accessToken ?? null;

  const handleFailure = useCallback(
    (failure: unknown) => {
      if (failure instanceof ApiError && failure.status === 401) {
        clearSession(); // expired, invalid or deactivated
        navigate("/login", { replace: true });
      } else {
        setState("error");
      }
    },
    [navigate],
  );

  // The hubs to choose from.
  useEffect(() => {
    if (token === null) return;
    let active = true;
    listHubs(token)
      .then((loaded) => {
        if (!active) return;
        setHubs(loaded);
        setHubId(loaded[0]?.id ?? "");
        setState(loaded.length === 0 ? "ready" : "loading");
      })
      .catch((failure: unknown) => {
        if (active) handleFailure(failure);
      });
    return () => {
      active = false;
    };
  }, [token, handleFailure]);

  // The vehicles of the chosen hub, again whenever a choice changes. A late answer to an older
  // choice is ignored, so the list always matches what is selected on the page.
  useEffect(() => {
    if (token === null || hubId === "") return;
    let active = true;
    setSelected(null);
    searchHubVehicles(hubId, { type, minutes }, token)
      .then((found) => {
        if (!active) return;
        setVehicles(found);
        setState("ready");
      })
      .catch((failure: unknown) => {
        if (active) handleFailure(failure);
      });
    return () => {
      active = false;
    };
  }, [token, hubId, type, minutes, handleFailure]);

  if (session === null) {
    return <Navigate to="/login" replace />;
  }

  const nothingToBook = state === "ready" && !vehicles.some((vehicle) => vehicle.bookable);

  return (
    <main className="page">
      <Logo />
      <section className="card card-wide">
        <h1>Find a vehicle</h1>
        <p className="muted">
          <Link to="/">Back to home</Link>
        </p>

        {state === "error" && (
          <p role="alert" className="form-error">
            {GENERIC_ERROR}
          </p>
        )}

        {state !== "error" && hubs.length === 0 && state === "ready" && <p>{NO_HUBS}</p>}

        {hubs.length > 0 && (
          <form className="search-filters" aria-label="Search" onSubmit={(e) => e.preventDefault()}>
            <div className="field">
              <label htmlFor="search-hub">Hub</label>
              <select id="search-hub" value={hubId} onChange={(e) => setHubId(e.target.value)}>
                {hubs.map((hub) => (
                  <option key={hub.id} value={hub.id}>
                    {hubLabel(hub)}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="search-type">Vehicle type</label>
              <select
                id="search-type"
                value={type ?? ""}
                onChange={(e) => setType(e.target.value === "" ? null : (e.target.value as VehicleType))}
              >
                <option value="">All types</option>
                {VEHICLE_TYPES.map((option) => (
                  <option key={option} value={option}>
                    {TYPE_LABELS[option]}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="search-ride">Ride length</label>
              <select
                id="search-ride"
                value={minutes === null ? "" : String(minutes)}
                onChange={(e) => setMinutes(e.target.value === "" ? null : Number(e.target.value))}
              >
                <option value="">Not sure yet</option>
                {RIDE_LENGTHS.map((option) => (
                  <option key={option.minutes} value={option.minutes}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
          </form>
        )}

        {state === "loading" && <p>Loading vehicles…</p>}

        {nothingToBook && hubs.length > 0 && <p aria-live="polite">{NOTHING_TO_BOOK}</p>}

        {state === "ready" && vehicles.length > 0 && (
          <div className="table-scroll">
            <table className="fleet-table" aria-label="Vehicles at this hub">
              <thead>
                <tr>
                  <th scope="col">Vehicle</th>
                  <th scope="col">Type</th>
                  <th scope="col">Battery</th>
                  <th scope="col">Status</th>
                  <th scope="col">
                    <span className="visually-hidden">Choose</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {vehicles.map((vehicle) => (
                  <tr key={vehicle.id} className={vehicle.bookable ? undefined : "unbookable"}>
                    <th scope="row">{vehicle.code}</th>
                    <td>{TYPE_LABELS[vehicle.type]}</td>
                    <td>{vehicle.batteryPercent === null ? "Unknown" : `${vehicle.batteryPercent}%`}</td>
                    <td>{unbookableText(vehicle)}</td>
                    <td>
                      <button
                        type="button"
                        className="button button-small"
                        disabled={!vehicle.bookable}
                        aria-pressed={selected === vehicle.id}
                        onClick={() => setSelected(vehicle.id)}
                      >
                        {selected === vehicle.id ? "Selected" : "Select"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {selected !== null && (
          <p aria-live="polite">
            Selected: {vehicles.find((vehicle) => vehicle.id === selected)?.code}
          </p>
        )}
      </section>
    </main>
  );
}
