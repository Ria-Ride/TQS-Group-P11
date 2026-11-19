import { useState } from "react";
import { changeRole, setActive, type Account, type Role } from "../api/admin";
import { ASSIGNABLE_ROLES, ROLE_LABELS } from "../pages/roles";
import { GENERIC_ERROR } from "../pages/messages";

interface AccountRowProps {
  account: Account;
  token: string;
  onChanged: (account: Account) => void;
}

/**
 * One account in the admin's table. Admin accounts have no controls: they are managed directly in
 * the database, and the API would refuse the change (409).
 */
export function AccountRow({ account, token, onChanged }: AccountRowProps) {
  const [role, setRole] = useState<Role>(account.role);
  const [fieldTechnician, setFieldTechnician] = useState(account.fieldTechnician);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const isAdmin = account.role === "ADMIN";
  const canBeTechnician = role === "OPERATOR";

  async function run(change: () => Promise<Account>) {
    setBusy(true);
    setError(null);
    try {
      onChanged(await change());
    } catch {
      setError(GENERIC_ERROR);
    } finally {
      setBusy(false);
    }
  }

  return (
    <tr>
      <th scope="row">{account.name}</th>
      <td>{account.email}</td>
      <td>
        {isAdmin ? (
          ROLE_LABELS.ADMIN
        ) : (
          <select
            aria-label={`Role for ${account.name}`}
            value={role}
            onChange={(event) => setRole(event.target.value as Role)}
          >
            {ASSIGNABLE_ROLES.map((option) => (
              <option key={option} value={option}>
                {ROLE_LABELS[option]}
              </option>
            ))}
          </select>
        )}
      </td>
      <td>
        {isAdmin ? (
          "—"
        ) : (
          <label className="checkbox">
            <input
              type="checkbox"
              checked={canBeTechnician && fieldTechnician}
              disabled={!canBeTechnician}
              onChange={(event) => setFieldTechnician(event.target.checked)}
            />
            Field technician
          </label>
        )}
      </td>
      <td>{account.active ? "Active" : "Inactive"}</td>
      <td>
        {!isAdmin && (
          <div className="row-actions">
            <button
              type="button"
              className="button button-small"
              disabled={busy}
              onClick={() =>
                void run(() =>
                  changeRole(account.id, role, canBeTechnician && fieldTechnician, token),
                )
              }
            >
              Save
            </button>
            <button
              type="button"
              className="button button-secondary button-small"
              disabled={busy}
              onClick={() => void run(() => setActive(account.id, !account.active, token))}
            >
              {account.active ? "Deactivate" : "Activate"}
            </button>
            {error && (
              <p role="alert" className="field-error">
                {error}
              </p>
            )}
          </div>
        )}
      </td>
    </tr>
  );
}