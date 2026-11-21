import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { registerGuest } from "../api/auth";
import { ApiError, type FieldErrors } from "../api/client";
import { saveSession } from "../auth/session";
import { AuthLayout } from "../components/AuthLayout";
import { TextField } from "../components/TextField";
import { friendlyFieldErrors, GENERIC_ERROR } from "./messages";

const FIELD_MESSAGES = {
  name: "Enter your name (up to 100 characters).",
  email: "Enter a valid email address, like ana@example.com.",
  password: "Use between 8 and 72 characters.",
};
const EMAIL_IN_USE = "This email already has an account.";

export function RegisterPage() {
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [emailInUse, setEmailInUse] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function submit() {
    setSubmitting(true);
    setFieldErrors({});
    setFormError(null);
    setEmailInUse(false);
    try {
      const grant = await registerGuest({ name, email, password });
      saveSession({ userId: grant.userId, accessToken: grant.accessToken });
      navigate("/", { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setEmailInUse(true);
        setFieldErrors({ email: EMAIL_IN_USE });
      } else if (error instanceof ApiError && error.status === 400) {
        setFieldErrors(friendlyFieldErrors(error.fieldErrors, FIELD_MESSAGES));
      } else {
        setFormError(GENERIC_ERROR);
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout title="Create your guest account">
      <p className="muted">Only your name and email. No institutional account needed.</p>
      <form
        className="form"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        {formError && (
          <p role="alert" className="form-error">
            {formError}
          </p>
        )}
        <TextField
          id="name"
          label="Name"
          autoComplete="name"
          value={name}
          onChange={setName}
          error={fieldErrors.name}
        />
        <TextField
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={setEmail}
          error={fieldErrors.email}
        >
          {emailInUse && (
            <>
              <Link to="/login">Log in</Link> instead.
            </>
          )}
        </TextField>
        <TextField
          id="password"
          label="Password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={setPassword}
          error={fieldErrors.password}
        />
        <button type="submit" className="button" disabled={submitting}>
          Create account
        </button>
      </form>
      <p className="switch">
        Already have an account? <Link to="/login">Go to login</Link>
      </p>
    </AuthLayout>
  );
}