import { useState } from "react";
import { ApiError, type FieldErrors } from "../api/client";
import type { Vehicle } from "../api/fleet";
import { openTicket } from "../api/maintenance";
import { friendlyFieldErrors, GENERIC_ERROR } from "../pages/messages";
import {
  DESCRIPTION_MESSAGE,
  FAULT_CODE_MESSAGE,
  FAULT_COVERED,
  VEHICLE_MESSAGE,
  VEHICLE_RETIRED,
} from "../pages/ticketLabels";
import { TextField } from "./TextField";

const FIELD_MESSAGES = {
  vehicleId: VEHICLE_MESSAGE,
  faultCode: FAULT_CODE_MESSAGE,
  description: DESCRIPTION_MESSAGE,
};

interface NewTicketFormProps {
  vehicles: Vehicle[];
  token: string;
  onCreated: () => void;
}

/**
 * US10: an operator opens a ticket for a fault the simulator cannot detect (BR18, BR19). Retired
 * vehicles are not offered: they are out of the fleet for good, and the API refuses them too.
 */
export function NewTicketForm({ vehicles, token, onCreated }: NewTicketFormProps) {
  const choices = vehicles.filter((vehicle) => vehicle.state !== "RETIRED");
  const [chosenVehicle, setChosenVehicle] = useState("");
  const [faultCode, setFaultCode] = useState("");
  const [description, setDescription] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // The first vehicle until the operator chooses another (or if the chosen one disappeared).
  const vehicleId = choices.some((vehicle) => vehicle.id === chosenVehicle)
    ? chosenVehicle
    : (choices[0]?.id ?? "");

  async function submit() {
    setSubmitting(true);
    setFieldErrors({});
    setFormError(null);
    try {
      await openTicket({ vehicleId, faultCode, description }, token);
      setFaultCode("");
      setDescription("");
      onCreated();
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setFieldErrors(friendlyFieldErrors(error.fieldErrors, FIELD_MESSAGES));
      } else if (error instanceof ApiError && error.status === 409 && error.fieldErrors.faultCode) {
        setFieldErrors({ faultCode: FAULT_COVERED });
      } else if (error instanceof ApiError && error.status === 409) {
        setFieldErrors({ vehicleId: VEHICLE_RETIRED });
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
      aria-labelledby="new-ticket-title"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <h3 id="new-ticket-title">New ticket</h3>
      {choices.length === 0 && <p className="muted">Register a vehicle first.</p>}
      {formError && (
        <p role="alert" className="form-error">
          {formError}
        </p>
      )}
      <div className="field">
        <label htmlFor="ticket-vehicle">Vehicle</label>
        <select
          id="ticket-vehicle"
          value={vehicleId}
          disabled={choices.length === 0}
          aria-invalid={fieldErrors.vehicleId ? true : undefined}
          aria-describedby={fieldErrors.vehicleId ? "ticket-vehicle-error" : undefined}
          onChange={(event) => setChosenVehicle(event.target.value)}
        >
          {choices.map((vehicle) => (
            <option key={vehicle.id} value={vehicle.id}>
              {vehicle.code}
            </option>
          ))}
        </select>
        {fieldErrors.vehicleId && (
          <p id="ticket-vehicle-error" className="field-error">
            {fieldErrors.vehicleId}
          </p>
        )}
      </div>
      <TextField
        id="ticket-fault-code"
        label="Fault code"
        value={faultCode}
        onChange={setFaultCode}
        error={fieldErrors.faultCode}
      />
      <div className="field">
        <label htmlFor="ticket-description">Description</label>
        <textarea
          id="ticket-description"
          value={description}
          maxLength={500}
          rows={3}
          aria-invalid={fieldErrors.description ? true : undefined}
          aria-describedby={fieldErrors.description ? "ticket-description-error" : undefined}
          onChange={(event) => setDescription(event.target.value)}
        />
        {fieldErrors.description && (
          <p id="ticket-description-error" className="field-error">
            {fieldErrors.description}
          </p>
        )}
      </div>
      <button type="submit" className="button" disabled={submitting || choices.length === 0}>
        Open ticket
      </button>
    </form>
  );
}
