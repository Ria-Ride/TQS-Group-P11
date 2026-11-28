import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router";
import { bookForLater, listVehicleTypes, startRide, type Booking } from "../api/bookings";
import { ApiError } from "../api/client";
import {
  listHubs,
  searchHubVehicles,
  type Hub,
  type HubVehicle,
  type VehicleType,
} from "../api/fleet";
import { listPasses, type Pass } from "../api/passes";
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
import { TEST_CARDS } from "./testCards";

type PageState = "loading" | "ready" | "error";

const NO_HUBS = "There are no hubs yet.";
const NOTHING_TO_BOOK = "No vehicle is available at this hub right now.";
const DEPOSITS_UNKNOWN = "The deposit cannot be shown right now. Please try again later.";

/**
 * How long a ride can be planned for (BR5: at most 4 hours by default). The server stays the
 * authority: a ride it refuses is explained to the tourist.
 */
const START_LENGTHS: { minutes: number; label: string }[] = [
  { minutes: 30, label: "30 minutes" },
  { minutes: 60, label: "1 hour" },
  { minutes: 120, label: "2 hours" },
  { minutes: 180, label: "3 hours" },
  { minutes: 240, label: "4 hours" },
];
const DEFAULT_START_MINUTES = 60;

/** The backend's refusals of a start (US04), written for tourists. */
const START_MESSAGES = {
  vehicleId: "This vehicle was just taken or cannot do this ride. Choose another one.",
  pass: "You need a valid pass that allows this vehicle type.",
  booking: "You already have a ride in progress.",
  plannedEnd: "This ride is too long for the rules or for your pass. Choose a shorter one.",
  plannedStart: "Choose a start time in the future.",
  402: "Your deposit was declined. No ride was started and nothing was held.",
  504: "The deposit did not answer in time. No ride was started; please try again.",
} as const;

const WHEN = new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" });

/** US05: a ride for later must fit in one of the tourist's passes (BR4). */
const NO_PASS_FOR_LATER = "You need a pass that is valid when the ride starts.";
const LATER_OVERLAP = "You already have a booking in that period. Choose another time.";

/** A Date as a datetime-local value ("YYYY-MM-DDTHH:mm") in the browser's zone. */
function toLocalInput(at: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())}` +
    `T${pad(at.getHours())}:${pad(at.getMinutes())}`
  );
}

/**
 * The latest start that lets a ride of this length end inside one of the passes, or null when no
 * pass covers a ride after now. The API stays the authority on the rules.
 */
function latestStart(passes: Pass[], rideMinutes: number, now: number): Date | null {
  if (passes.length === 0) return null;
  const lastEnd = Math.max(...passes.map((pass) => new Date(pass.validTo).getTime()));
  const latest = lastEnd - rideMinutes * 60_000;
  return latest > now ? new Date(latest) : null;
}

/** A refused start: what to tell the tourist, and what the page offers next. */
interface StartError {
  message: string;
  /** No valid pass for this vehicle: offer to buy one. */
  buyPass: boolean;
  /** The vehicle may have just been taken: search the hub again. */
  searchAgain: boolean;
}

function startError(failure: unknown, later: boolean): StartError {
  const refusal = (message: string, buyPass = false, searchAgain = false): StartError => ({
    message,
    buyPass,
    searchAgain,
  });
  if (!(failure instanceof ApiError)) {
    return refusal(GENERIC_ERROR);
  }
  const fields = failure.fieldErrors;
  if (failure.status === 402) return refusal(START_MESSAGES[402]);
  if (failure.status === 504) return refusal(START_MESSAGES[504]);
  if ("vehicleId" in fields) return refusal(START_MESSAGES.vehicleId, false, true);
  if ("pass" in fields) return refusal(START_MESSAGES.pass, true);
  if ("booking" in fields) return refusal(later ? LATER_OVERLAP : START_MESSAGES.booking);
  if ("plannedStart" in fields) return refusal(START_MESSAGES.plannedStart);
  if ("plannedEnd" in fields) return refusal(START_MESSAGES.plannedEnd);
  return refusal(GENERIC_ERROR);
}

/** The ride length picked in the search, if the start offers it; otherwise one hour. */
function startLengthFor(minutes: number | null): number {
  return START_LENGTHS.some((option) => option.minutes === minutes)
    ? (minutes as number)
    : DEFAULT_START_MINUTES;
}

/** A started ride and the code of its vehicle, to show it to the tourist. */
interface StartedRide {
  booking: Booking;
  vehicleCode: string;
}

/**
 * US02: a tourist picks a hub, optionally a vehicle type and the length of the ride, and sees the
 * vehicles of the hub with their battery. A vehicle that cannot be booked (BR1, BR2) is listed with
 * the reason and cannot be selected. Which vehicles can be booked is decided by the API, not here.
 *
 * US04: selecting a bookable vehicle opens the start of a ride: its duration, the deposit of the
 * vehicle's type (shown before confirming, BR11) and a test card of the simulated gateway. The
 * rules are checked by the API; a refusal is explained in words for tourists.
 *
 * US05: the tourist can instead book the vehicle for later, picking the start; the picker is
 * bounded by the end of the tourist's passes (BR4). The vehicle is then Reserved until it is
 * started from My bookings in its window (BR7), cancelled, or expires.
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
  const [searchRound, setSearchRound] = useState(0);

  const [deposits, setDeposits] = useState<Partial<Record<VehicleType, string>> | null>(null);
  const [depositsFailed, setDepositsFailed] = useState(false);
  const [rideMinutes, setRideMinutes] = useState(DEFAULT_START_MINUTES);
  const [card, setCard] = useState<string>(TEST_CARDS[0].token);
  const [busy, setBusy] = useState(false);
  const [refusal, setRefusal] = useState<StartError | null>(null);
  const [ride, setRide] = useState<StartedRide | null>(null);
  // US05: start now (US04) or book for a later period.
  const [later, setLater] = useState(false);
  const [startAt, setStartAt] = useState("");
  const [passes, setPasses] = useState<Pass[] | null>(null);

  const token = session?.accessToken ?? null;

  const logInAgain = useCallback(() => {
    clearSession(); // expired, invalid or deactivated
    navigate("/login", { replace: true });
  }, [navigate]);

  const handleFailure = useCallback(
    (failure: unknown) => {
      if (failure instanceof ApiError && failure.status === 401) {
        logInAgain();
      } else {
        setState("error");
      }
    },
    [logInAgain],
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

  // The deposit of each vehicle type, to show it before a ride is started (BR11). Without it the
  // search still works, but no ride can be started.
  useEffect(() => {
    if (token === null) return;
    let active = true;
    listVehicleTypes(token)
      .then((types) => {
        if (!active) return;
        const byType: Partial<Record<VehicleType, string>> = {};
        for (const vehicleType of types) {
          byType[vehicleType.type] = `${vehicleType.deposit} ${vehicleType.currency}`;
        }
        setDeposits(byType);
      })
      .catch((failure: unknown) => {
        if (!active) return;
        if (failure instanceof ApiError && failure.status === 401) {
          logInAgain();
        } else {
          setDepositsFailed(true);
        }
      });
    return () => {
      active = false;
    };
  }, [token, logInAgain]);

  // The vehicles of the chosen hub, again whenever a choice changes or a start asks for a fresh
  // search. A late answer to an older choice is ignored, so the list always matches the page.
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
  }, [token, hubId, type, minutes, searchRound, handleFailure]);

  if (session === null) {
    return <Navigate to="/login" replace />;
  }

  const nothingToBook = state === "ready" && !vehicles.some((vehicle) => vehicle.bookable);
  const chosen = vehicles.find((vehicle) => vehicle.id === selected);
  const deposit = chosen === undefined ? undefined : deposits?.[chosen.type];

  function choose(vehicleId: string) {
    setSelected(vehicleId);
    setRideMinutes(startLengthFor(minutes));
    setRefusal(null);
  }

  /** US05: the passes bound a ride for later; read once, when the tourist first asks for one. */
  function chooseLater() {
    setLater(true);
    if (token === null || passes !== null) return;
    listPasses(token)
      .then(setPasses)
      .catch((failure: unknown) => {
        if (failure instanceof ApiError && failure.status === 401) {
          logInAgain();
        } else {
          setPasses([]);
        }
      });
  }

  const latest = passes === null ? null : latestStart(passes, rideMinutes, Date.now());
  const noPassForLater = later && passes !== null && latest === null;

  async function start() {
    if (token === null || chosen === undefined) return;
    setBusy(true);
    setRefusal(null);
    const startsAt = later ? new Date(startAt).getTime() : Date.now();
    const plannedEnd = new Date(startsAt + rideMinutes * 60_000).toISOString();
    try {
      const booking = later
        ? await bookForLater(
            {
              vehicleId: chosen.id,
              plannedStart: new Date(startsAt).toISOString(),
              plannedEnd,
              paymentToken: card,
            },
            token,
          )
        : await startRide({ vehicleId: chosen.id, plannedEnd, paymentToken: card }, token);
      setRide({ booking, vehicleCode: chosen.code });
    } catch (failure: unknown) {
      if (failure instanceof ApiError && failure.status === 401) {
        logInAgain();
        return;
      }
      const refused = startError(failure, later);
      setRefusal(refused);
      if (refused.searchAgain) {
        setSearchRound((round) => round + 1);
      }
    } finally {
      setBusy(false);
    }
  }

  const startHub =
    ride === null ? undefined : hubs.find((hub) => hub.id === ride.booking.startHubId);

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
                        onClick={() => choose(vehicle.id)}
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

        {refusal !== null && (
          <div role="alert" className="form-error">
            <p>{refusal.message}</p>
            {refusal.buyPass && (
              <Link className="button button-small" to="/passes/buy">
                Buy a pass
              </Link>
            )}
          </div>
        )}

        {ride !== null && ride.booking.state === "RESERVED" && (
          <section aria-labelledby="ride-title" aria-live="polite">
            <h2 id="ride-title">Your vehicle is reserved</h2>
            <dl className="pass-details">
              <dt>Vehicle</dt>
              <dd>{ride.vehicleCode}</dd>
              <dt>At hub</dt>
              <dd>{startHub === undefined ? ride.booking.startHubId : hubLabel(startHub)}</dd>
              <dt>Start between</dt>
              <dd>
                {WHEN.format(new Date(ride.booking.plannedStart))}
                {ride.booking.startWindowEndsAt !== null &&
                  ` and ${WHEN.format(new Date(ride.booking.startWindowEndsAt))}`}
              </dd>
              <dt>Planned end</dt>
              <dd>{WHEN.format(new Date(ride.booking.plannedEnd))}</dd>
              <dt>Deposit held</dt>
              <dd>{`${ride.booking.deposit} ${ride.booking.currency}`}</dd>
            </dl>
            <p>
              Start it at the hub from <Link to="/bookings">My bookings</Link>, or cancel it there
              to get the whole deposit back.
            </p>
          </section>
        )}

        {ride !== null && ride.booking.state !== "RESERVED" && (
          <section aria-labelledby="ride-title" aria-live="polite">
            <h2 id="ride-title">Your ride has started</h2>
            <dl className="pass-details">
              <dt>Vehicle</dt>
              <dd>{ride.vehicleCode}</dd>
              <dt>From hub</dt>
              <dd>{startHub === undefined ? ride.booking.startHubId : hubLabel(startHub)}</dd>
              <dt>Planned end</dt>
              <dd>{WHEN.format(new Date(ride.booking.plannedEnd))}</dd>
              <dt>Deposit held</dt>
              <dd>{`${ride.booking.deposit} ${ride.booking.currency}`}</dd>
            </dl>
            <p>The vehicle is being unlocked.</p>
            <p>
              End the ride at any hub from <Link to="/bookings">My bookings</Link>.
            </p>
          </section>
        )}

        {ride === null && chosen !== undefined && (
          <section aria-labelledby="start-title">
            <h2 id="start-title">Start a ride</h2>
            <p aria-live="polite">Selected: {chosen.code}</p>
            <form
              className="form"
              aria-label="Start a ride"
              onSubmit={(event) => {
                event.preventDefault();
                void start();
              }}
            >
              <div className="field">
                <span id="start-when-label" className="field-label">
                  When
                </span>
                <div role="radiogroup" aria-labelledby="start-when-label" className="choice-list">
                  <label className="choice">
                    <input
                      type="radio"
                      name="start-when"
                      checked={!later}
                      onChange={() => setLater(false)}
                    />
                    <span>Now</span>
                  </label>
                  <label className="choice">
                    <input type="radio" name="start-when" checked={later} onChange={chooseLater} />
                    <span>Later</span>
                  </label>
                </div>
              </div>

              {later && (
                <div className="field">
                  <label htmlFor="start-at">Start at</label>
                  <input
                    id="start-at"
                    type="datetime-local"
                    value={startAt}
                    min={toLocalInput(new Date(Date.now() + 60_000))}
                    max={latest === null ? undefined : toLocalInput(latest)}
                    onChange={(e) => setStartAt(e.target.value)}
                  />
                  {noPassForLater && (
                    <p className="muted">
                      {NO_PASS_FOR_LATER} <Link to="/passes/buy">Buy a pass</Link>
                    </p>
                  )}
                </div>
              )}

              <div className="field">
                <label htmlFor="start-duration">Ride duration</label>
                <select
                  id="start-duration"
                  value={String(rideMinutes)}
                  onChange={(e) => setRideMinutes(Number(e.target.value))}
                >
                  {START_LENGTHS.map((option) => (
                    <option key={option.minutes} value={option.minutes}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>

              {deposit !== undefined && <p>Deposit held during the ride: {deposit}</p>}
              {deposit === undefined && depositsFailed && <p className="muted">{DEPOSITS_UNKNOWN}</p>}

              <div className="field">
                <span id="start-card-label" className="field-label">
                  Test card
                </span>
                <p className="muted">
                  This is a simulated deposit: no real money is held. Choose how the bank answers.
                </p>
                <div role="radiogroup" aria-labelledby="start-card-label" className="choice-list">
                  {TEST_CARDS.map((option) => (
                    <label key={option.token} className="choice">
                      <input
                        type="radio"
                        name="start-card"
                        value={option.token}
                        checked={option.token === card}
                        onChange={() => setCard(option.token)}
                      />
                      <span>{option.label}</span>
                    </label>
                  ))}
                </div>
              </div>

              <button
                type="submit"
                className="button"
                disabled={
                  busy || deposit === undefined || (later && (startAt === "" || noPassForLater))
                }
              >
                {later ? "Book for later" : "Start ride"}
              </button>
            </form>
          </section>
        )}
      </section>
    </main>
  );
}