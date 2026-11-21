import { useState } from "react";
import { createAccount, type Account, type Role } from "../api/admin";
import { ApiError, type FieldErrors } from "../api/client";
import { ASSIGNABLE_ROLES, ROLE_LABELS } from "../pages/roles";
import { friendlyFieldErrors, GENERIC_ERROR } from "../pages/messages";
import { TextField } from "./TextField";

const FIELD_MESSAGES = {
  name: "Enter the person's name.",
  email: "Enter a valid email.",
  password: "Use at least 8 characters.",
  role: "Choose a role.",
  fieldTechnician: "Only an operator can be a field technician.",
};
const EMAIL_IN_USE = "This email is already in use.";

interface CreateAccountFormProps {
  token: string;
  onCreated: (account: Account) => void;
}

/** US13: an admin creates an account; only an OPERATOR can be a field technician (BR20). */
export function CreateAccountForm({ token, onCreated }: CreateAccountFormProps) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<Role>("OPERATOR");
  const [fieldTechnician, setFieldTechnician] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const canBeTechnician = role === "OPERATOR";

  async function submit() {
    setSubmitting(true);
    setFieldErrors({});
    setFormError(null);
    try {
      const created = await createAccount(
        { name, email, password, role, fieldTechnician: canBeTechnician && fieldTechnician },
        token,
      );
      onCreated(created);
      setName("");
      setEmail("");
      setPassword("");
      setFieldTechnician(false);
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setFieldErrors(friendlyFieldErrors(error.fieldErrors, FIELD_MESSAGES));
      } else if (error instanceof ApiError && error.status === 409) {
        setFieldErrors({ email: EMAIL_IN_USE });
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
      aria-labelledby="new-account-title"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <h2 id="new-account-title">New account</h2>
      {formError && (
        <p role="alert" className="form-error">
          {formError}
        </p>
      )}
      <TextField id="name" label="Name" value={name} onChange={setName} error={fieldErrors.name} />
      <TextField
        id="email"
        label="Email"
        type="email"
        autoComplete="off"
        value={email}
        onChange={setEmail}
        error={fieldErrors.email}
      />
      <TextField
        id="password"
        label="Password"
        type="password"
        autoComplete="new-password"
        value={password}
        onChange={setPassword}
        error={fieldErrors.password}
      />
      <div className="field">
        <label htmlFor="role">Role</label>
        <select id="role" value={role} onChange={(event) => setRole(event.target.value as Role)}>
          {ASSIGNABLE_ROLES.map((option) => (
            <option key={option} value={option}>
              {ROLE_LABELS[option]}
            </option>
          ))}
        </select>
      </div>
      <label className="checkbox">
        <input
          type="checkbox"
          checked={canBeTechnician && fieldTechnician}
          disabled={!canBeTechnician}
          aria-invalid={fieldErrors.fieldTechnician ? true : undefined}
          onChange={(event) => setFieldTechnician(event.target.checked)}
        />
        Field technician
      </label>
      {fieldErrors.fieldTechnician && <p className="field-error">{fieldErrors.fieldTechnician}</p>}
      <button type="submit" className="button" disabled={submitting}>
        Create account
      </button>
    </form>
  );
}