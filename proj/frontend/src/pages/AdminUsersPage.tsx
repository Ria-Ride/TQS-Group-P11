import { useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router";
import { listAccounts, type Account } from "../api/admin";
import { getProfile } from "../api/auth";
import { ApiError } from "../api/client";
import { clearSession, loadSession } from "../auth/session";
import { AccountRow } from "../components/AccountRow";
import { CreateAccountForm } from "../components/CreateAccountForm";
import { Logo } from "../components/Logo";
import { GENERIC_ERROR } from "./messages";

type PageState = "loading" | "forbidden" | "ready" | "error";

const NO_ACCESS = "You do not have access to this page.";

function byName(a: Account, b: Account) {
  return a.name.localeCompare(b.name);
}

/**
 * US13: account management for platform admins. Hiding it from other roles is only for the user;
 * the API itself answers 403 to anyone who is not an ADMIN (BR23).
 */
export function AdminUsersPage() {
  const navigate = useNavigate();
  const [session] = useState(loadSession);
  const [state, setState] = useState<PageState>("loading");
  const [accounts, setAccounts] = useState<Account[]>([]);

  useEffect(() => {
    if (session === null) {
      return;
    }
    // Ignore a late answer if the page was left meanwhile.
    let active = true;
    async function load(token: string, userId: string) {
      try {
        const profile = await getProfile(userId, token);
        if (!active) return;
        if (profile.role !== "ADMIN") {
          setState("forbidden");
          return;
        }
        const loaded = await listAccounts(token);
        if (!active) return;
        setAccounts(loaded);
        setState("ready");
      } catch (failure: unknown) {
        if (!active) return;
        if (failure instanceof ApiError && failure.status === 401) {
          clearSession(); // expired, invalid or deactivated
          navigate("/login", { replace: true });
        } else if (failure instanceof ApiError && failure.status === 403) {
          setState("forbidden");
        } else {
          setState("error");
        }
      }
    }
    void load(session.accessToken, session.userId);
    return () => {
      active = false;
    };
  }, [session, navigate]);

  if (session === null) {
    return <Navigate to="/login" replace />;
  }

  function replaceAccount(changed: Account) {
    setAccounts((current) => current.map((a) => (a.id === changed.id ? changed : a)));
  }

  function addAccount(created: Account) {
    setAccounts((current) => [...current, created].sort(byName));
  }

  return (
    <main className="page">
      <Logo />
      <section className="card card-wide" aria-live="polite">
        <h1>Manage accounts</h1>
        {state === "loading" && <p>Loading accounts…</p>}
        {state === "forbidden" && (
          <p role="alert" className="form-error">
            {NO_ACCESS}
          </p>
        )}
        {state === "error" && (
          <p role="alert" className="form-error">
            {GENERIC_ERROR}
          </p>
        )}
        {state === "ready" && (
          <>
            <div className="table-scroll">
              <table className="accounts">
                <thead>
                  <tr>
                    <th scope="col">Name</th>
                    <th scope="col">Email</th>
                    <th scope="col">Role</th>
                    <th scope="col">Technician</th>
                    <th scope="col">Status</th>
                    <th scope="col">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {accounts.map((account) => (
                    <AccountRow
                      key={account.id}
                      account={account}
                      token={session.accessToken}
                      onChanged={replaceAccount}
                    />
                  ))}
                </tbody>
              </table>
            </div>
            <CreateAccountForm token={session.accessToken} onCreated={addAccount} />
          </>
        )}
        <p className="switch">
          <Link to="/">Back to home</Link>
        </p>
      </section>
    </main>
  );
}