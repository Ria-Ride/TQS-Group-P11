import { useEffect, useState } from "react";
import { updatePassType, type PassTypeChange } from "../api/backoffice";
import { ApiError } from "../api/client";
import type { VehicleType } from "../api/fleet";
import type { PassType } from "../api/passes";
import { MONEY_MESSAGE, PASS_TYPE_MESSAGES } from "../pages/backofficeMessages";
import { parseNumber, TYPE_LABELS } from "../pages/fleetLabels";
import { GENERIC_ERROR } from "../pages/messages";

interface PassTypeRowProps {
  passType: PassType;
  token: string;
  onChanged: () => void;
}

/** One pass type in the client manager's table: its price and validity to edit (BR8). */
export function PassTypeRow({ passType, token, onChanged }: PassTypeRowProps) {
  const [price, setPrice] = useState(passType.price);
  const [days, setDays] = useState(String(passType.validityDays));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Show the saved values again when the list is reloaded.
  useEffect(() => {
    setPrice(passType.price);
    setDays(String(passType.validityDays));
  }, [passType.price, passType.validityDays]);

  async function save() {
    const validityDays = parseNumber(days);
    if (validityDays === null) {
      setError(PASS_TYPE_MESSAGES.validityDays);
      return;
    }
    // Only what changed is sent, so a price change never rewrites the validity.
    const change: PassTypeChange = {};
    if (price.trim() !== passType.price) change.price = price.trim();
    if (validityDays !== passType.validityDays) change.validityDays = validityDays;
    setBusy(true);
    setError(null);
    try {
      await updatePassType(passType.id, change, token);
      onChanged();
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 400) {
        setError(failure.fieldErrors.validityDays ? PASS_TYPE_MESSAGES.validityDays : MONEY_MESSAGE);
      } else {
        setError(GENERIC_ERROR);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <tr>
      <th scope="row">{passType.code}</th>
      <td>{passType.name}</td>
      <td>
        <input
          className="inline-input"
          aria-label={`Price of ${passType.code}`}
          inputMode="decimal"
          value={price}
          onChange={(event) => setPrice(event.target.value)}
        />{" "}
        {passType.currency}
      </td>
      <td>
        <input
          className="inline-input"
          aria-label={`Days of ${passType.code}`}
          inputMode="numeric"
          value={days}
          onChange={(event) => setDays(event.target.value)}
        />
      </td>
      <td>{passType.vehicleTypes.map((type) => TYPE_LABELS[type as VehicleType] ?? type).join(", ")}</td>
      <td>
        <button
          type="button"
          className="button button-small"
          disabled={busy}
          onClick={() => void save()}
        >
          Save
        </button>
        {error && (
          <p role="alert" className="field-error">
            {error}
          </p>
        )}
      </td>
    </tr>
  );
}
