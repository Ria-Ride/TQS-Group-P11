import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router";
import {
  cancelBooking,
  endRide,
  listBookings,
  nearbyHubsOf,
  startBooking,
  type Booking,
  type BookingState,
  type NearbyHub,
} from "../api/bookings";
import { ApiError } from "../api/client";
import { listHubs, type Hub } from "../api/fleet";
import { clearSession, loadSession } from "../auth/session";
import { Logo } from "../components/Logo";
import { hubLabel } from "./fleetLabels";
import { GENERIC_ERROR } from "./messages";

type PageState = "loading" | "ready" | "error";

const NO_BOOKINGS = "You have no bookings yet.";
const TOO_EARLY = "It is too early to start this ride. Come back at its planned start.";
const NO_LONGER = "This booking can no longer be started or cancelled. Refresh to see its state.";
const HUB_FULL =
  "This hub is full. End at a nearby hub with room: if you do it soon, overtime is counted only " +
  "until now.";
const NOT_LOCKED = "The vehicle could not be locked. Park it inside the hub and try again.";
const NO_ANSWER = "The vehicle did not answer. Try again in a moment.";
const NOT_ACTIVE = "This ride has already ended. Refresh to see it.";

/** How often the page looks at the clock, so the start action appears when its window opens. */
const CLOCK_TICK_MS = 15_000;

const STATE_LABELS: Record<BookingState, string> = {
  RESERVED: "Reserved",
  ACTIVE: "In progress",
  COMPLETED: "Ended",
  CANCELLED: "Cancelled",
  EXPIRED: "Expired",
};

const WHEN = new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" });

/** BR7: a Reserved booking can be started from its planned start to the end of its window. */
function inStartWindow(booking: Booking, now: number): boolean {
  if (booking.state !== "RESERVED" || booking.startWindowEndsAt === null) return false;
  return (
    now >= new Date(booking.plannedStart).getTime() &&
    now <= new Date(booking.startWindowEndsAt).getTime()
  );
}

/** A refused start or cancel, in words for tourists. */
function actionError(failure: unknown): string {
  if (failure instanceof ApiError && failure.status === 409) {
    const reason = failure.fieldErrors.booking;
    return reason === "cannot be started before its planned start" ? TOO_EARLY : NO_LONGER;
  }
  return GENERIC_ERROR;
}

/** A refused end of a ride (US06), in words for tourists; the ride goes on in every case. */
function endError(failure: unknown): string {
  if (!(failure instanceof ApiError)) return GENERIC_ERROR;
  if (failure.status === 504) return NO_ANSWER;
  const fields = failure.fieldErrors;
  if ("hubId" in fields && failure.status === 409) return HUB_FULL;
  if ("vehicleId" in fields) return NOT_LOCKED;
  if ("booking" in fields) return NOT_ACTIVE;
  return GENERIC_ERROR;
}

function nearbyLabel(nearby: NearbyHub): string {
  return `End at ${nearby.code} · ${nearby.name} (${nearby.freeSlots} free, ${Math.round(nearby.distanceMeters)} m)`;
}

/**
 * US05: the tourist's bookings. A Reserved one shows when it can be started (BR7) and can be
 * cancelled, which gives the whole deposit back (BR13). The start action is offered only inside the
 * window; the API stays the authority and its refusals are explained.
 *
 * US06: an Active ride is ended at a hub the tourist picks. A full hub is explained and its nearby
 * hubs with room are offered as one-click ends (BR14, BR15). An ended ride shows its overtime
 * charge, what the deposit paid and what was given back (BR12, BR13).
 */
export function MyBookingsPage() {
  const navigate = useNavigate();
  const [session] = useState(loadSession);
  const [state, setState] = useState<PageState>("loading");
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [now, setNow] = useState(() => Date.now());
  const [busy, setBusy] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [hubs, setHubs] = useState<Hub[]>([]);
  const [endHub, setEndHub] = useState<Record<string, string>>({});
  const [nearby, setNearby] = useState<Record<string, NearbyHub[]>>({});

  const token = session?.accessToken ?? null;

  const logInAgain = useCallback(() => {
    clearSession();
    navigate("/login", { replace: true });
  }, [navigate]);

  useEffect(() => {
    if (token === null) return;
    let active = true;
    listBookings(token)
      .then((loaded) => {
        if (!active) return;
        setBookings(loaded);
        setState("ready");
      })
      .catch((failure: unknown) => {
        if (!active) return;
        if (failure instanceof ApiError && failure.status === 401) {
          logInAgain();
        } else {
          setState("error");
        }
      });
    return () => {
      active = false;
    };
  }, [token, logInAgain]);

  // The hubs a ride can end at. Without them only the suggestions of a full hub are offered.
  useEffect(() => {
    if (token === null) return;
    let active = true;
    listHubs(token)
      .then((loaded) => {
        if (active) setHubs(loaded);
      })
      .catch(() => {
        // the page still works; ending needs a hub, which the next load may bring
      });
    return () => {
      active = false;
    };
  }, [token]);

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), CLOCK_TICK_MS);
    return () => clearInterval(timer);
  }, []);

  if (session === null || token === null) {
    return <Navigate to="/login" replace />;
  }

  async function act(booking: Booking, action: typeof startBooking) {
    if (token === null) return;
    setBusy(booking.id);
    setErrors((previous) => ({ ...previous, [booking.id]: "" }));
    try {
      const changed = await action(booking.id, token);
      setBookings((previous) => previous.map((b) => (b.id === changed.id ? changed : b)));
    } catch (failure: unknown) {
      if (failure instanceof ApiError && failure.status === 401) {
        logInAgain();
        return;
      }
      setErrors((previous) => ({ ...previous, [booking.id]: actionError(failure) }));
    } finally {
      setBusy(null);
    }
  }

  async function end(booking: Booking, hubId: string) {
    if (token === null || hubId === "") return;
    setBusy(booking.id);
    setErrors((previous) => ({ ...previous, [booking.id]: "" }));
    setNearby((previous) => ({ ...previous, [booking.id]: [] }));
    try {
      const changed = await endRide(booking.id, hubId, token);
      setBookings((previous) => previous.map((b) => (b.id === changed.id ? changed : b)));
    } catch (failure: unknown) {
      if (failure instanceof ApiError && failure.status === 401) {
        logInAgain();
        return;
      }
      setErrors((previous) => ({ ...previous, [booking.id]: endError(failure) }));
      setNearby((previous) => ({ ...previous, [booking.id]: nearbyHubsOf(failure) }));
    } finally {
      setBusy(null);
    }
  }

  return (
    <main className="page">
      <Logo />
      <section className="card card-wide">
        <h1>My bookings</h1>
        <p className="muted">
          <Link to="/">Back to home</Link> · <Link to="/vehicles/search">Find a vehicle</Link>
        </p>

        {state === "loading" && <p>Loading your bookings…</p>}
        {state === "error" && (
          <p role="alert" className="form-error">
            {GENERIC_ERROR}
          </p>
        )}
        {state === "ready" && bookings.length === 0 && <p>{NO_BOOKINGS}</p>}

        {state === "ready" &&
          bookings.map((booking) => {
            const deposit = `${booking.deposit} ${booking.currency}`;
            const canStart = inStartWindow(booking, now);
            const error = errors[booking.id];
            return (
              <article
                key={booking.id}
                className="booking-card"
                aria-label={`Booking ${booking.id}`}
              >
                <h2>{WHEN.format(new Date(booking.plannedStart))}</h2>
                <dl className="pass-details">
                  <dt>State</dt>
                  <dd>{STATE_LABELS[booking.state]}</dd>
                  <dt>Planned end</dt>
                  <dd>{WHEN.format(new Date(booking.plannedEnd))}</dd>
                  <dt>Deposit</dt>
                  <dd>{deposit}</dd>
                </dl>

                {booking.state === "RESERVED" && booking.startWindowEndsAt !== null && (
                  <p>
                    Start it between {WHEN.format(new Date(booking.plannedStart))} and{" "}
                    {WHEN.format(new Date(booking.startWindowEndsAt))} at the hub. If you do not,
                    it expires and your deposit is given back.
                  </p>
                )}
                {booking.state === "CANCELLED" && (
                  <p>Your deposit of {deposit} was given back.</p>
                )}

                {booking.state === "COMPLETED" && (
                  <dl className="pass-details">
                    <dt>Overtime charge</dt>
                    <dd className="overtime">{`${booking.overtimeCharge} ${booking.currency}`}</dd>
                    <dt>Taken from the deposit</dt>
                    <dd className="captured">{`${booking.depositCaptured} ${booking.currency}`}</dd>
                    <dt>Given back</dt>
                    <dd className="released">{`${booking.depositReleased} ${booking.currency}`}</dd>
                  </dl>
                )}

                {error && (
                  <p role="alert" className="form-error">
                    {error}
                  </p>
                )}

                {booking.state === "ACTIVE" && (
                  <form
                    className="form"
                    aria-label="End the ride"
                    onSubmit={(event) => {
                      event.preventDefault();
                      void end(booking, endHub[booking.id] ?? hubs[0]?.id ?? "");
                    }}
                  >
                    <div className="field">
                      <label htmlFor={`end-hub-${booking.id}`}>End at hub</label>
                      <select
                        id={`end-hub-${booking.id}`}
                        value={endHub[booking.id] ?? hubs[0]?.id ?? ""}
                        onChange={(e) =>
                          setEndHub((previous) => ({ ...previous, [booking.id]: e.target.value }))
                        }
                      >
                        {hubs.map((option) => (
                          <option key={option.id} value={option.id}>
                            {hubLabel(option)}
                          </option>
                        ))}
                      </select>
                    </div>
                    <button
                      type="submit"
                      className="button"
                      disabled={busy === booking.id || hubs.length === 0}
                    >
                      End ride
                    </button>
                    {(nearby[booking.id] ?? []).length > 0 && (
                      <ul className="choice-list" aria-label="Nearby hubs with room">
                        {(nearby[booking.id] ?? []).map((suggestion) => (
                          <li key={suggestion.id}>
                            <button
                              type="button"
                              className="button button-secondary"
                              disabled={busy === booking.id}
                              onClick={() => void end(booking, suggestion.id)}
                            >
                              {nearbyLabel(suggestion)}
                            </button>
                          </li>
                        ))}
                      </ul>
                    )}
                  </form>
                )}

                {booking.state === "RESERVED" && (
                  <div className="home-actions">
                    <button
                      type="button"
                      className="button"
                      disabled={!canStart || busy === booking.id}
                      onClick={() => void act(booking, startBooking)}
                    >
                      Start ride
                    </button>
                    <button
                      type="button"
                      className="button button-secondary"
                      disabled={busy === booking.id}
                      onClick={() => void act(booking, cancelBooking)}
                    >
                      Cancel booking
                    </button>
                  </div>
                )}
              </article>
            );
          })}
      </section>
    </main>
  );
}
