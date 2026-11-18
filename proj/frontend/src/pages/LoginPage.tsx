import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { login } from "../api/auth";
import { ApiError, type FieldErrors } from "../api/client";
import { saveSession } from "../auth/session";
import { AuthLayout } from "../components/AuthLayout";
import { TextField } from "../components/TextField";
import { friendlyFieldErrors, GENERIC_ERROR } from "./messages";

const FIELD_MESSAGES = {
  email: "Enter your email.",
  password: "Enter your password.",
};
const INVALID_CREDENTIALS = "The email or password is incorrect.";

export function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit() {
    setSubmitting(true);
    setFieldErrors({});
    setFormError(null);
    try {
      const grant = await login({ email, password });
      saveSession({ userId: grant.userId, accessToken: grant.accessToken });
      navigate("/", { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setFormError(INVALID_CREDENTIALS);
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
    <AuthLayout title="Log in">
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
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={setEmail}
          error={fieldErrors.email}
        />
        <TextField
          id="password"
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={setPassword}
          error={fieldErrors.password}
        />
        <button type="submit" className="button" disabled={submitting}>
          Log in
        </button>
      </form>
      <p className="switch">
        New here? <Link to="/register">Create a guest account</Link>
      </p>
    </AuthLayout>
  );
}