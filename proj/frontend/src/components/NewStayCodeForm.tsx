import { useState } from "react";
import { issueStayCode, type Hotel } from "../api/backoffice";
import { ApiError, type FieldErrors } from "../api/client";
import { CHECK_OUT_BEFORE_CHECK_IN, STAY_CODE_MESSAGES } from "../pages/backofficeMessages";
import { friendlyFieldErrors, GENERIC_ERROR } from "../pages/messages";
import { TextField } from "./TextField";

interface NewStayCodeFormProps {
  hotels: Hotel[];
  token: string;
  onIssued: () => void;
}

/** US14, BR9: the client manager issues a stay code; the platform makes the code itself. */
export function NewStayCodeForm({ hotels, token, onIssued }: NewStayCodeFormProps) {
  const [hotel, setHotel] = useState("");
  const [checkIn, setCheckIn] = useState("");
  const [checkOut, setCheckOut] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [issued, setIssued] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setFieldErrors({});
    setFormError(null);
    setIssued(null);
    try {
      const stay = await issueStayCode({ accommodationId: hotel, checkIn, checkOut }, token);
      setIssued(stay.code);
      onIssued();
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        const friendly = friendlyFieldErrors(error.fieldErrors, STAY_CODE_MESSAGES);
        // The server says "before the check-in" when both days are there but in the wrong order.
        if (error.fieldErrors.checkOut?.includes("before")) {
          friendly.checkOut = CHECK_OUT_BEFORE_CHECK_IN;
        }
        setFieldErrors(friendly);
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
      aria-label="New stay code"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <h3>New stay code</h3>
      {hotels.length === 0 && <p className="muted">Register a partner hotel first.</p>}
      <div className="field">
        <label htmlFor="stay-hotel">Hotel</label>
        <select
          id="stay-hotel"
          value={hotel}
          aria-invalid={fieldErrors.accommodationId ? true : undefined}
          onChange={(event) => setHotel(event.target.value)}
        >
          <option value="">Choose a hotel</option>
          {hotels.map((option) => (
            <option key={option.id} value={option.id}>
              {option.name}
            </option>
          ))}
        </select>
        {fieldErrors.accommodationId && <p className="field-error">{fieldErrors.accommodationId}</p>}
      </div>
      <TextField
        id="stay-check-in"
        label="Check-in"
        type="date"
        value={checkIn}
        onChange={setCheckIn}
        error={fieldErrors.checkIn}
      />
      <TextField
        id="stay-check-out"
        label="Check-out"
        type="date"
        value={checkOut}
        onChange={setCheckOut}
        error={fieldErrors.checkOut}
      />
      {formError && (
        <p role="alert" className="form-error">
          {formError}
        </p>
      )}
      {issued && <p role="status">Code issued: {issued}</p>}
      <button type="submit" className="button" disabled={busy}>
        Issue code
      </button>
    </form>
  );
}
