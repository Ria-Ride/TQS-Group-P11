import { useState } from "react";
import { ApiError, type FieldErrors } from "../api/client";
import { registerVehicle, type Hub, type VehicleType } from "../api/fleet";
import {
  CODE_IN_USE,
  CODE_MESSAGE,
  HUB_FULL,
  hubLabel,
  TYPE_LABELS,
  VEHICLE_TYPES,
} from "../pages/fleetLabels";
import { friendlyFieldErrors, GENERIC_ERROR } from "../pages/messages";
import { TextField } from "./TextField";

const FIELD_MESSAGES = {
  code: CODE_MESSAGE,
  type: "Choose a type.",
  hubId: "Choose a hub.",
};

interface NewVehicleFormProps {
  hubs: Hub[];
  token: string;
  onCreated: () => void;
}

/** US08: an operator registers a vehicle at a hub with a free slot; it starts Deactivated (BR14). */
export function NewVehicleForm({ hubs, token, onCreated }: NewVehicleFormProps) {
  const [code, setCode] = useState("");
  const [type, setType] = useState<VehicleType>("E_BIKE");
  const [chosenHub, setChosenHub] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // The first hub until the operator chooses another (or if the chosen one disappeared).
  const hubId = hubs.some((hub) => hub.id === chosenHub) ? chosenHub : (hubs[0]?.id ?? "");

  async function submit() {
    setSubmitting(true);
    setFieldErrors({});
    setFormError(null);
    try {
      await registerVehicle({ code, type, hubId }, token);
      setCode("");
      onCreated();
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setFieldErrors(friendlyFieldErrors(error.fieldErrors, FIELD_MESSAGES));
      } else if (error instanceof ApiError && error.status === 409 && error.fieldErrors.hubId) {
        setFieldErrors({ hubId: HUB_FULL });
      } else if (error instanceof ApiError && error.status === 409) {
        setFieldErrors({ code: CODE_IN_USE });
      } else {
        setFormError(GENERIC_ERROR);
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form
      className="form"
      aria-labelledby="new-vehicle-title"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <h3 id="new-vehicle-title">New vehicle</h3>
      {hubs.length === 0 && <p className="muted">Register a hub first.</p>}
      {formError && (
        <p role="alert" className="form-error">
          {formError}
        </p>
      )}
      <TextField
        id="vehicle-code"
        label="Code"
        value={code}
        onChange={setCode}
        error={fieldErrors.code}
      />
      <div className="field">
        <label htmlFor="vehicle-type">Type</label>
        <select
          id="vehicle-type"
          value={type}
          onChange={(event) => setType(event.target.value as VehicleType)}
        >
          {VEHICLE_TYPES.map((option) => (
            <option key={option} value={option}>
              {TYPE_LABELS[option]}
            </option>
          ))}
        </select>
      </div>
      <div className="field">
        <label htmlFor="vehicle-hub">Hub</label>
        <select
          id="vehicle-hub"
          value={hubId}
          disabled={hubs.length === 0}
          aria-invalid={fieldErrors.hubId ? true : undefined}
          aria-describedby={fieldErrors.hubId ? "vehicle-hub-error" : undefined}
          onChange={(event) => setChosenHub(event.target.value)}
        >
          {hubs.map((hub) => (
            <option key={hub.id} value={hub.id}>
              {hubLabel(hub)}
            </option>
          ))}
        </select>
        {fieldErrors.hubId && (
          <p id="vehicle-hub-error" className="field-error">
            {fieldErrors.hubId}
          </p>
        )}
      </div>
      <button type="submit" className="button" disabled={submitting || hubs.length === 0}>
        Register vehicle
      </button>
    </form>
  );
}
