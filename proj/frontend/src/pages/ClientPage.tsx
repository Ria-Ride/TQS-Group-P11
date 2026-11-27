import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router";
import { getProfile } from "../api/auth";
import {
  getRules,
  listHotels,
  listRulesVersions,
  listStayCodes,
  type Hotel,
  type RulesOfUse,
  type StayCode,
} from "../api/backoffice";
import { ApiError } from "../api/client";
import { listHubs, type Hub } from "../api/fleet";
import { listPassTypes, type PassType } from "../api/passes";
import { clearSession, loadSession } from "../auth/session";
import { DepositsTable } from "../components/DepositsTable";
import { HubRow } from "../components/HubRow";
import { Logo } from "../components/Logo";
import { NewHotelForm } from "../components/NewHotelForm";
import { NewPassTypeForm } from "../components/NewPassTypeForm";
import { NewStayCodeForm } from "../components/NewStayCodeForm";
import { PassTypeRow } from "../components/PassTypeRow";
import { RulesOfUseForm } from "../components/RulesOfUseForm";
import { GENERIC_ERROR } from "./messages";

type PageState = "loading" | "forbidden" | "ready" | "error";

const NO_ACCESS = "You do not have access to this page.";
const CLIENT_ROLES = ["CLIENT_MANAGER", "ADMIN"];

interface Data {
  rules: RulesOfUse;
  versions: RulesOfUse[];
  hubs: Hub[];
  passTypes: PassType[];
  hotels: Hotel[];
  stayCodes: StayCode[];
}

async function loadAll(token: string): Promise<Data> {
  const [rules, versions, hubs, passTypes, hotels, stayCodes] = await Promise.all([
    getRules(token),
    listRulesVersions(token),
    listHubs(token),
    listPassTypes(token),
    listHotels(token),
    listStayCodes(token),
  ]);
  return { rules, versions, hubs, passTypes, hotels, stayCodes };
}

/**
 * US14: the client manager sets the rules of use and the deposits, the capacity of the hubs, the
 * pass types, and the partner hotels and their stay codes. Hiding it from other roles is only for
 * the user; the API itself refuses them (BR23). After every change everything is read again, so
 * the page shows what the server holds, including the new version of the rules (BR21).
 */
export function ClientPage() {
  const navigate = useNavigate();
  const [session] = useState(loadSession);
  const [state, setState] = useState<PageState>("loading");
  const [data, setData] = useState<Data | null>(null);

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
      setData(await loadAll(session.accessToken));
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
        if (!CLIENT_ROLES.includes(profile.role)) {
          setState("forbidden");
          return;
        }
        const loaded = await loadAll(token);
        if (!active) return;
        setData(loaded);
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

  const token = session.accessToken;
  const reload = () => void refresh();

  return (
    <main className="page">
      <Logo />
      <section className="card card-wide" aria-live="polite">
        <h1>Client management</h1>
        <p className="muted">
          <Link to="/">Back to home</Link>
        </p>
        {state === "loading" && <p>Loading…</p>}
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
        {state === "ready" && data && (
          <>
            <h2>Rules of use</h2>
            <RulesOfUseForm
              rules={data.rules}
              versions={data.versions}
              token={token}
              onSaved={reload}
            />

            <h2>Deposits</h2>
            <DepositsTable vehicleTypes={data.rules.vehicleTypes} token={token} onChanged={reload} />

            <h2 id="hubs-title">Hub capacity</h2>
            {data.hubs.length === 0 && <p className="muted">No hubs yet.</p>}
            <div className="table-scroll">
              <table className="fleet-table" aria-label="Hubs">
                <thead>
                  <tr>
                    <th scope="col">Code</th>
                    <th scope="col">Name</th>
                    <th scope="col">Vehicles</th>
                    <th scope="col">Capacity</th>
                  </tr>
                </thead>
                <tbody>
                  {data.hubs.map((hub) => (
                    <HubRow key={hub.id} hub={hub} token={token} onChanged={reload} />
                  ))}
                </tbody>
              </table>
            </div>

            <h2>Pass types</h2>
            <div className="table-scroll">
              <table className="fleet-table" aria-label="Pass types">
                <thead>
                  <tr>
                    <th scope="col">Code</th>
                    <th scope="col">Name</th>
                    <th scope="col">Price</th>
                    <th scope="col">Days</th>
                    <th scope="col">Vehicles</th>
                    <th scope="col">
                      <span className="visually-hidden">Change</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {data.passTypes.map((passType) => (
                    <PassTypeRow
                      key={passType.id}
                      passType={passType}
                      token={token}
                      onChanged={reload}
                    />
                  ))}
                </tbody>
              </table>
            </div>
            <NewPassTypeForm token={token} onCreated={reload} />

            <h2>Stay codes</h2>
            <div className="table-scroll">
              <table className="fleet-table" aria-label="Hotels">
                <thead>
                  <tr>
                    <th scope="col">Hotel</th>
                    <th scope="col">Discount</th>
                  </tr>
                </thead>
                <tbody>
                  {data.hotels.map((hotel) => (
                    <tr key={hotel.id}>
                      <th scope="row">{hotel.name}</th>
                      <td>{hotel.discountPercent}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <NewHotelForm token={token} onCreated={reload} />
            <NewStayCodeForm hotels={data.hotels} token={token} onIssued={reload} />
            <div className="table-scroll">
              <table className="fleet-table" aria-label="Stay codes">
                <thead>
                  <tr>
                    <th scope="col">Code</th>
                    <th scope="col">Hotel</th>
                    <th scope="col">Valid</th>
                    <th scope="col">Discount</th>
                    <th scope="col">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {data.stayCodes.map((stay) => (
                    <tr key={stay.id}>
                      <th scope="row">{stay.code}</th>
                      <td>{stay.accommodationName}</td>
                      <td>
                        {stay.checkIn} to {stay.checkOut}
                      </td>
                      <td>{stay.discountPercent}%</td>
                      <td>{stay.redeemed ? "Used" : "Not used"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </section>
    </main>
  );
}
