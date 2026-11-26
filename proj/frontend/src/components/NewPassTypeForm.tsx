import { useState } from "react";
import { createPassType } from "../api/backoffice";
import { ApiError, type FieldErrors } from "../api/client";
import type { VehicleType } from "../api/fleet";
import { PASS_TYPE_CODE_IN_USE, PASS_TYPE_MESSAGES } from "../pages/backofficeMessages";
import { parseNumber, TYPE_LABELS, VEHICLE_TYPES } from "../pages/fleetLabels";
import { friendlyFieldErrors, GENERIC_ERROR } from "../pages/messages";
import { TextField } from "./TextField";

interface NewPassTypeFormProps {
  token: string;
  onCreated: () => void;
}

/** US14, BR8: the client manager creates a pass type: code, name, validity, price, vehicle types. */
export function NewPassTypeForm({ token, onCreated }: NewPassTypeFormProps) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [days, setDays] = useState("");
  const [price, setPrice] = useState("");
  const [types, setTypes] = useState<VehicleType[]>(VEHICLE_TYPES);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function toggle(type: VehicleType) {
    setTypes((current) =>
      current.includes(type) ? current.filter((other) => other !== type) : [...current, type],
    );
  }

  async function submit() {
    setBusy(true);
    setFieldErrors({});
    setFormError(null);
    try {
      await createPassType(
        {
          code: code.trim(),
          name: name.trim(),
          validityDays: parseNumber(days),
          price: price.trim(),
          vehicleTypes: types,
        },
        token,
      );
      setCode("");
      setName("");
      setDays("");
      setPrice("");
      setTypes(VEHICLE_TYPES);
      onCreated();
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setFieldErrors(friendlyFieldErrors(error.fieldErrors, PASS_TYPE_MESSAGES));
      } else if (error instanceof ApiError && error.status === 409) {
        setFieldErrors({ code: PASS_TYPE_CODE_IN_USE });
      } else {
        setFormError(GENERIC_ERROR);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <form
      className="form"
      aria-label="New pass type"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <h3>New pass type</h3>
      <TextField id="pass-type-code" label="Code" value={code} onChange={setCode} error={fieldErrors.code} />
      <TextField id="pass-type-name" label="Name" value={name} onChange={setName} error={fieldErrors.name} />
      <TextField
        id="pass-type-days"
        label="Valid for (days)"
        inputMode="numeric"
        value={days}
        onChange={setDays}
        error={fieldErrors.validityDays}
      />
      <TextField
        id="pass-type-price"
        label="Price (EUR)"
        inputMode="decimal"
        value={price}
        onChange={setPrice}
        error={fieldErrors.price}
      />
      <fieldset>
        <legend>Allowed vehicle types</legend>
        {VEHICLE_TYPES.map((type) => (
          <label key={type} className="checkbox">
            <input type="checkbox" checked={types.includes(type)} onChange={() => toggle(type)} />
            {TYPE_LABELS[type]}
          </label>
        ))}
        {fieldErrors.vehicleTypes && <p className="field-error">{fieldErrors.vehicleTypes}</p>}
      </fieldset>
      {formError && (
        <p role="alert" className="form-error">
          {formError}
        </p>
      )}
      <button type="submit" className="button" disabled={busy}>
        Create pass type
      </button>
    </form>
  );
}
