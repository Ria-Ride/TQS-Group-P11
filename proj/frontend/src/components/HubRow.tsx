import { useEffect, useState } from "react";
import { ApiError } from "../api/client";
import { changeCapacity, type Hub } from "../api/fleet";
import { CAPACITY_BELOW_HELD, CAPACITY_MESSAGE, parseNumber } from "../pages/fleetLabels";
import { GENERIC_ERROR } from "../pages/messages";

interface HubRowProps {
  hub: Hub;
  token: string;
  onChanged: () => void;
}

/** One hub in the operator's table: vehicles held against capacity, and the capacity to edit. */
export function HubRow({ hub, token, onChanged }: HubRowProps) {
  const [capacity, setCapacity] = useState(String(hub.capacity));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Show the saved capacity again when the list is reloaded.
  useEffect(() => setCapacity(String(hub.capacity)), [hub.capacity]);

  async function save() {
    setBusy(true);
    setError(null);
    try {
      await changeCapacity(hub.id, parseNumber(capacity), token);
      onChanged();
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 409) {
        setError(CAPACITY_BELOW_HELD);
      } else if (failure instanceof ApiError && failure.status === 400) {
        setError(CAPACITY_MESSAGE);
      } else {
        setError(GENERIC_ERROR);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <tr>
      <th scope="row">{hub.code}</th>
      <td>{hub.name}</td>
      <td>
        {hub.vehiclesHeld} of {hub.capacity}
      </td>
      <td>
        <div className="row-actions">
          <input
            className="inline-input"
            aria-label={`Capacity of ${hub.code}`}
            inputMode="numeric"
            value={capacity}
            onChange={(event) => setCapacity(event.target.value)}
          />
          <button
            type="button"
            className="button button-small"
            disabled={busy}
            onClick={() => void save()}
          >
            Save
          </button>
        </div>
        {error && (
          <p role="alert" className="field-error">
            {error}
          </p>
        )}
      </td>
    </tr>
  );
}
