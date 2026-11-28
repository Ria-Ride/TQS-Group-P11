import { useState } from "react";
import { changeDeposit, type VehicleTypeRules } from "../api/backoffice";
import { ApiError } from "../api/client";
import { MONEY_MESSAGE } from "../pages/backofficeMessages";
import { TYPE_LABELS } from "../pages/fleetLabels";
import { GENERIC_ERROR } from "../pages/messages";

interface DepositRowProps {
  rules: VehicleTypeRules;
  token: string;
  onChanged: () => void;
}

/** One vehicle type: the deposit held for a booking (BR4) to edit. */
function DepositRow({ rules, token, onChanged }: DepositRowProps) {
  const [deposit, setDeposit] = useState(rules.depositAmount);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const label = TYPE_LABELS[rules.type];

  // Show the saved deposit again when the rules are reloaded. Done while rendering, not in an
  // effect: an effect could run late, after the user started typing, and overwrite the input.
  const [shown, setShown] = useState(rules.depositAmount);
  if (shown !== rules.depositAmount) {
    setShown(rules.depositAmount);
    setDeposit(rules.depositAmount);
  }

  async function save() {
    setBusy(true);
    setError(null);
    try {
      await changeDeposit(rules.type, deposit.trim(), token);
      onChanged();
    } catch (failure) {
      setError(
        failure instanceof ApiError && failure.status === 400 ? MONEY_MESSAGE : GENERIC_ERROR,
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <tr>
      <th scope="row">{label}</th>
      <td>{rules.batteryPercentPer30Minutes}% per 30 minutes</td>
      <td>
        <div className="row-actions">
          <input
            className="inline-input"
            aria-label={`Deposit of ${label}`}
            inputMode="decimal"
            value={deposit}
            onChange={(event) => setDeposit(event.target.value)}
          />
          <span>{rules.currency}</span>
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

interface DepositsTableProps {
  vehicleTypes: VehicleTypeRules[];
  token: string;
  onChanged: () => void;
}

/** US14, BR4: the deposit of each vehicle type. A change makes a new version of the rules (BR21). */
export function DepositsTable({ vehicleTypes, token, onChanged }: DepositsTableProps) {
  return (
    <div className="table-scroll">
      <table className="fleet-table" aria-label="Deposits">
        <thead>
          <tr>
            <th scope="col">Vehicle type</th>
            <th scope="col">Battery use</th>
            <th scope="col">Deposit</th>
          </tr>
        </thead>
        <tbody>
          {vehicleTypes.map((rules) => (
            <DepositRow key={rules.type} rules={rules} token={token} onChanged={onChanged} />
          ))}
        </tbody>
      </table>
    </div>
  );
}
