import { useState } from "react";
import { ApiError } from "../api/client";
import {
  transition,
  vehicleHistory,
  type Hub,
  type StateChange,
  type Vehicle,
  type VehicleAction,
} from "../api/fleet";
import {
  ACTION_LABELS,
  ACTIONS_BY_STATE,
  HUB_FULL,
  hubLabel,
  STATE_LABELS,
  TRANSITION_REFUSED,
  TYPE_LABELS,
} from "../pages/fleetLabels";
import { GENERIC_ERROR } from "../pages/messages";

interface VehicleRowProps {
  vehicle: Vehicle;
  hubs: Hub[];
  token: string;
  onChanged: () => void;
}

function describe(change: StateChange): string {
  const from = change.from === null ? "New" : STATE_LABELS[change.from];
  return `${from} → ${STATE_LABELS[change.to]}`;
}

/**
 * One vehicle in the operator's table, with the actions its state allows (BR16). Retiring asks
 * first, because no transition leads out of Retired.
 */
export function VehicleRow({ vehicle, hubs, token, onChanged }: VehicleRowProps) {
  const [chosenHub, setChosenHub] = useState("");
  const [confirmingRetire, setConfirmingRetire] = useState(false);
  const [history, setHistory] = useState<StateChange[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const hub = hubs.find((candidate) => candidate.id === vehicle.hubId);
  const destination = hubs.some((h) => h.id === chosenHub) ? chosenHub : (hubs[0]?.id ?? "");

  async function run(action: VehicleAction) {
    setBusy(true);
    setError(null);
    setConfirmingRetire(false);
    try {
      if (action === "CONFIRM_ARRIVAL") {
        await transition(vehicle.id, action, token, destination);
      } else {
        await transition(vehicle.id, action, token);
      }
      setHistory(null);
      onChanged();
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 409 && failure.fieldErrors.hubId) {
        setError(HUB_FULL);
      } else if (failure instanceof ApiError && failure.status === 409) {
        setError(TRANSITION_REFUSED);
      } else {
        setError(GENERIC_ERROR);
      }
    } finally {
      setBusy(false);
    }
  }

  async function toggleHistory() {
    if (history !== null) {
      setHistory(null);
      return;
    }
    setError(null);
    try {
      setHistory(await vehicleHistory(vehicle.id, token));
    } catch {
      setError(GENERIC_ERROR);
    }
  }

  function actionButton(action: VehicleAction) {
    if (action === "RETIRE") {
      return (
        <button
          key={action}
          type="button"
          className="button button-secondary button-small"
          disabled={busy}
          onClick={() => setConfirmingRetire(true)}
        >
          {ACTION_LABELS.RETIRE}
        </button>
      );
    }
    if (action === "CONFIRM_ARRIVAL") {
      return (
        <span key={action} className="row-actions">
          <select
            aria-label={`Destination of ${vehicle.code}`}
            value={destination}
            onChange={(event) => setChosenHub(event.target.value)}
          >
            {hubs.map((option) => (
              <option key={option.id} value={option.id}>
                {hubLabel(option)}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="button button-small"
            disabled={busy || hubs.length === 0}
            onClick={() => void run(action)}
          >
            {ACTION_LABELS.CONFIRM_ARRIVAL}
          </button>
        </span>
      );
    }
    return (
      <button
        key={action}
        type="button"
        className="button button-small"
        disabled={busy}
        onClick={() => void run(action)}
      >
        {ACTION_LABELS[action]}
      </button>
    );
  }

  return (
    <tr>
      <th scope="row">{vehicle.code}</th>
      <td>{TYPE_LABELS[vehicle.type]}</td>
      <td>{STATE_LABELS[vehicle.state]}</td>
      <td>{hub ? hub.code : "—"}</td>
      <td>
        {confirmingRetire ? (
          <div className="row-actions">
            <span>Retire {vehicle.code} for good?</span>
            <button
              type="button"
              className="button button-small"
              disabled={busy}
              onClick={() => void run("RETIRE")}
            >
              Yes, retire
            </button>
            <button
              type="button"
              className="button button-secondary button-small"
              onClick={() => setConfirmingRetire(false)}
            >
              Cancel
            </button>
          </div>
        ) : (
          <div className="row-actions">
            {ACTIONS_BY_STATE[vehicle.state].map(actionButton)}
            <button
              type="button"
              className="button button-secondary button-small"
              aria-expanded={history !== null}
              onClick={() => void toggleHistory()}
            >
              History
            </button>
          </div>
        )}
        {error && (
          <p role="alert" className="field-error">
            {error}
          </p>
        )}
        {history !== null && (
          <ol className="history" aria-label={`History of ${vehicle.code}`}>
            {history.map((change, index) => (
              <li key={index}>
                {describe(change)}{" "}
                <time dateTime={change.at}>{new Date(change.at).toLocaleString()}</time>
              </li>
            ))}
          </ol>
        )}
      </td>
    </tr>
  );
}
