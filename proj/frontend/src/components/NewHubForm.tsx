import { useState } from "react";
import { ApiError, type FieldErrors } from "../api/client";
import { registerHub } from "../api/fleet";
import { CAPACITY_MESSAGE, CODE_IN_USE, CODE_MESSAGE, parseNumber } from "../pages/fleetLabels";
import { friendlyFieldErrors, GENERIC_ERROR } from "../pages/messages";
import { TextField } from "./TextField";

const FIELD_MESSAGES = {
  code: CODE_MESSAGE,
  name: "Enter the hub's name.",
  latitude: "Enter a latitude between -90 and 90.",
  longitude: "Enter a longitude between -180 and 180.",
  capacity: CAPACITY_MESSAGE,
};

interface NewHubFormProps {
  token: string;
  onCreated: () => void;
}

/** US08: an operator registers a hub with its position and capacity (BR14). */
export function NewHubForm({ token, onCreated }: NewHubFormProps) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [latitude, setLatitude] = useState("");
  const [longitude, setLongitude] = useState("");
  const [capacity, setCapacity] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit() {
    setSubmitting(true);
    setFieldErrors({});
    setFormError(null);
    try {
      await registerHub(
        {
          code,
          name,
          latitude: parseNumber(latitude),
          longitude: parseNumber(longitude),
          capacity: parseNumber(capacity),
        },
        token,
      );
      setCode("");
      setName("");
      setLatitude("");
      setLongitude("");
      setCapacity("");
      onCreated();
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setFieldErrors(friendlyFieldErrors(error.fieldErrors, FIELD_MESSAGES));
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
      aria-labelledby="new-hub-title"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <h3 id="new-hub-title">New hub</h3>
      {formError && (
        <p role="alert" className="form-error">
          {formError}
        </p>
      )}
      <TextField
        id="hub-code"
        label="Code"
        value={code}
        onChange={setCode}
        error={fieldErrors.code}
      />
      <TextField
        id="hub-name"
        label="Name"
        value={name}
        onChange={setName}
        error={fieldErrors.name}
      />
      <div className="field-pair">
        <TextField
          id="hub-latitude"
          label="Latitude"
          inputMode="decimal"
          value={latitude}
          onChange={setLatitude}
          error={fieldErrors.latitude}
        />
        <TextField
          id="hub-longitude"
          label="Longitude"
          inputMode="decimal"
          value={longitude}
          onChange={setLongitude}
          error={fieldErrors.longitude}
        />
      </div>
      <TextField
        id="hub-capacity"
        label="Capacity"
        inputMode="numeric"
        value={capacity}
        onChange={setCapacity}
        error={fieldErrors.capacity}
      />
      <button type="submit" className="button" disabled={submitting}>
        Register hub
      </button>
    </form>
  );
}
