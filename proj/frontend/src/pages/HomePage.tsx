import { useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router";
import { getProfile, type UserProfile } from "../api/auth";
import { ApiError } from "../api/client";
import { clearSession, loadSession } from "../auth/session";
import { Logo } from "../components/Logo";
import { GENERIC_ERROR } from "./messages";

export function HomePage() {
  const navigate = useNavigate();
  const [session] = useState(loadSession);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (session === null) {
      return;
    }
    // Ignore a late answer if the page was left meanwhile.
    let active = true;
    getProfile(session.userId, session.accessToken)
      .then((loaded) => {
        if (active) setProfile(loaded);
      })
      .catch((failure: unknown) => {
        if (!active) return;
        if (failure instanceof ApiError && failure.status === 401) {
          clearSession(); // expired or invalid token
          navigate("/login", { replace: true });
        } else {
          setError(GENERIC_ERROR);
        }
      });
    return () => {
      active = false;
    };
  }, [session, navigate]);

  if (session === null) {
    return <Navigate to="/login" replace />;
  }

  function logOut() {
    clearSession();
    navigate("/login", { replace: true });
  }

  return (
    <main className="page">
      <Logo />
      <section className="card" aria-live="polite">
        {profile && (
          <>
            <h1>Hello, {profile.name}</h1>
            <p className="muted">You are logged in as {profile.email}.</p>
            <div className="home-actions">
              {profile.role === "END_USER" && (
                <Link className="button" to="/passes/buy">
                  Buy a pass
                </Link>
              )}
              <Link className="button" to="/vehicles/search">
                Find a vehicle
              </Link>
              {(profile.role === "OPERATOR" || profile.role === "ADMIN") && (
                <Link className="button" to="/operator/fleet">
                  Manage fleet
                </Link>
              )}
              {profile.role === "ADMIN" && (
                <Link className="button" to="/admin/users">
                  Manage accounts
                </Link>
              )}
            </div>
          </>
        )}
        {!profile && error && (
          <p role="alert" className="form-error">
            {error}
          </p>
        )}
        {!profile && !error && <p>Loading your account…</p>}
        <button type="button" className="button button-secondary" onClick={logOut}>
          Log out
        </button>
      </section>
    </main>
  );
}