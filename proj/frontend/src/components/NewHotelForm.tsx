import { useState } from "react";
import { registerHotel } from "../api/backoffice";
import { ApiError, type FieldErrors } from "../api/client";
import { HOTEL_MESSAGES, HOTEL_NAME_IN_USE } from "../pages/backofficeMessages";
import { parseNumber } from "../pages/fleetLabels";
import { friendlyFieldErrors, GENERIC_ERROR } from "../pages/messages";
import { TextField } from "./TextField";

interface NewHotelFormProps {
  token: string;
  onCreated: () => void;
}

/** US14, BR9: the client manager registers a partner hotel with the discount of its guests. */
export function NewHotelForm({ token, onCreated }: NewHotelFormProps) {
  const [name, setName] = useState("");
  const [discount, setDiscount] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setFieldErrors({});
    setFormError(null);
    try {
      await registerHotel({ name: name.trim(), discountPercent: parseNumber(discount) }, token);
      setName("");
      setDiscount("");
      onCreated();
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setFieldErrors(friendlyFieldErrors(error.fieldErrors, HOTEL_MESSAGES));
      } else if (error instanceof ApiError && error.status === 409) {
        setFieldErrors({ name: HOTEL_NAME_IN_USE });
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
      aria-label="New hotel"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <h3>New partner hotel</h3>
      <TextField id="hotel-name" label="Hotel name" value={name} onChange={setName} error={fieldErrors.name} />
      <TextField
        id="hotel-discount"
        label="Discount (%)"
        inputMode="numeric"
        value={discount}
        onChange={setDiscount}
        error={fieldErrors.discountPercent}
      />
      {formError && (
        <p role="alert" className="form-error">
          {formError}
        </p>
      )}
      <button type="submit" className="button" disabled={busy}>
        Register hotel
      </button>
    </form>
  );
}
