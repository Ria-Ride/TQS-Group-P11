import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router";
import { ApiError } from "../api/client";
import {
  buyPass,
  listPassTypes,
  quotePrice,
  type Pass,
  type PassType,
  type PriceQuote,
} from "../api/passes";
import { clearSession, loadSession } from "../auth/session";
import { Logo } from "../components/Logo";
import { GENERIC_ERROR } from "./messages";
import { TEST_CARDS } from "./testCards";

/** The backend's stay-code reasons (BR9), written for tourists. */
const STAY_CODE_MESSAGES: Record<string, string> = {
  "is unknown":
    "We do not know this stay code. Check it with your hotel, or continue without it at the normal price.",
  "is not valid today": "This stay code is not valid today: it only works from check-in to check-out.",
  "is linked to another account": "This stay code is already used by another account.",
};

const PAYMENT_MESSAGES: Record<number, string> = {
  402: "Your payment was declined. No pass was issued and nothing was charged.",
  504: "The payment did not answer in time. No pass was issued; please try again.",
};

const WHEN = new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" });

function validity(days: number): string {
  return days === 1 ? "valid 1 day" : `valid ${days} days`;
}

type PageState = "loading" | "ready" | "error";

/**
 * US03: a tourist chooses a pass, optionally applies the stay code of their partner hotel to see
 * the discounted price (BR9), chooses a test card of the simulated gateway and pays. The pass is
 * shown only after an approved payment (BR10). Prices and rules come from the API; the page only
 * shows them. Applying a code reserves nothing: the purchase checks it again.
 */
export function BuyPassPage() {
  const navigate = useNavigate();
  const [session] = useState(loadSession);
  const token = session?.accessToken ?? null;

  const [state, setState] = useState<PageState>("loading");
  const [types, setTypes] = useState<PassType[]>([]);
  const [typeId, setTypeId] = useState("");
  const [codeInput, setCodeInput] = useState("");
  const [appliedCode, setAppliedCode] = useState<string | null>(null);
  const [quote, setQuote] = useState<PriceQuote | null>(null);
  const [codeError, setCodeError] = useState<string | null>(null);
  const [card, setCard] = useState<string>(TEST_CARDS[0].token);
  const [busy, setBusy] = useState(false);
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [pass, setPass] = useState<Pass | null>(null);

  const handleFailure = useCallback(
    (failure: unknown): string | null => {
      if (failure instanceof ApiError && failure.status === 401) {
        clearSession(); // expired, invalid or deactivated
        navigate("/login", { replace: true });
        return null;
      }
      return GENERIC_ERROR;
    },
    [navigate],
  );

  /** A refused stay code, turned into a tourist message; null if the failure was something else. */
  function stayCodeMessage(failure: unknown): string | null {
    const reason = failure instanceof ApiError ? failure.fieldErrors.stayCode : undefined;
    return reason === undefined ? null : (STAY_CODE_MESSAGES[reason] ?? reason);
  }

  useEffect(() => {
    if (token === null) return;
    let active = true;
    listPassTypes(token)
      .then((loaded) => {
        if (!active) return;
        setTypes(loaded);
        setTypeId(loaded[0]?.id ?? "");
        setState("ready");
      })
      .catch((failure: unknown) => {
        if (active && handleFailure(failure) !== null) setState("error");
      });
    return () => {
      active = false;
    };
  }, [token, handleFailure]);

  // The discounted price follows the chosen pass type while a code is applied.
  useEffect(() => {
    if (token === null || appliedCode === null || typeId === "") return;
    let active = true;
    setBusy(true);
    quotePrice(typeId, appliedCode, token)
      .then((found) => {
        if (!active) return;
        setQuote(found);
        setCodeError(null);
      })
      .catch((failure: unknown) => {
        if (!active) return;
        setQuote(null);
        const message = stayCodeMessage(failure);
        if (message !== null) {
          setCodeError(message);
        } else {
          const other = handleFailure(failure);
          if (other !== null) setCodeError(other);
        }
      })
      .finally(() => {
        if (active) setBusy(false);
      });
    return () => {
      active = false;
    };
  }, [token, typeId, appliedCode, handleFailure]);

  if (session === null) {
    return <Navigate to="/login" replace />;
  }

  const chosen = types.find((type) => type.id === typeId);
  const discounted = quote !== null && quote.passTypeId === typeId ? quote : null;
  const price = discounted?.price ?? chosen?.price ?? "";
  const currency = chosen?.currency ?? "EUR";

  function applyCode() {
    setPaymentError(null);
    const typed = codeInput.trim();
    setAppliedCode(typed === "" ? null : typed);
    if (typed === "") {
      setQuote(null);
      setCodeError(null);
    }
  }

  function continueWithoutCode() {
    setCodeInput("");
    setAppliedCode(null);
    setQuote(null);
    setCodeError(null);
  }

  async function pay() {
    if (token === null || chosen === undefined) return;
    setBusy(true);
    setPaymentError(null);
    try {
      setPass(await buyPass({ passTypeId: chosen.id, stayCode: appliedCode, paymentToken: card }, token));
    } catch (failure: unknown) {
      const codeMessage = stayCodeMessage(failure);
      if (codeMessage !== null) {
        setQuote(null);
        setCodeError(codeMessage);
      } else if (failure instanceof ApiError && PAYMENT_MESSAGES[failure.status]) {
        setPaymentError(PAYMENT_MESSAGES[failure.status]);
      } else {
        const other = handleFailure(failure);
        if (other !== null) setPaymentError(other);
      }
    } finally {
      setBusy(false);
    }
  }

  if (pass !== null) {
    const name = types.find((type) => type.id === pass.passTypeId)?.name ?? pass.passTypeCode;
    return (
      <main className="page">
        <Logo />
        <section className="card" aria-labelledby="pass-title" aria-live="polite">
          <h1 id="pass-title">Your pass</h1>
          <p className="pass-issued">{name} is ready.</p>
          <dl className="pass-details">
            <dt>Paid</dt>
            <dd>
              {pass.price} {pass.currency}
            </dd>
            <dt>Valid from</dt>
            <dd>{WHEN.format(new Date(pass.validFrom))}</dd>
            <dt>Valid until</dt>
            <dd>{WHEN.format(new Date(pass.validTo))}</dd>
          </dl>
          <div className="home-actions">
            <Link className="button" to="/vehicles/search">
              Find a vehicle
            </Link>
            <Link className="button button-secondary" to="/">
              Back to home
            </Link>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="page">
      <Logo />
      <section className="card">
        <h1>Buy a pass</h1>
        <p className="muted">
          <Link to="/">Back to home</Link>
        </p>

        {state === "loading" && <p>Loading passes…</p>}
        {state === "error" && (
          <p role="alert" className="form-error">
            {GENERIC_ERROR}
          </p>
        )}

        {state === "ready" && chosen !== undefined && (
          <form
            className="form"
            aria-label="Buy a pass"
            onSubmit={(event) => {
              event.preventDefault();
              void pay();
            }}
          >
            <div className="field">
              <span id="pass-type-label" className="field-label">
                Pass
              </span>
              <div role="radiogroup" aria-labelledby="pass-type-label" className="choice-list">
                {types.map((type) => (
                  <label key={type.id} className="choice">
                    <input
                      type="radio"
                      name="pass-type"
                      value={type.id}
                      checked={type.id === typeId}
                      onChange={() => setTypeId(type.id)}
                    />
                    <span>
                      <strong>{type.name}</strong> · {type.price} {type.currency} ·{" "}
                      {validity(type.validityDays)}
                    </span>
                  </label>
                ))}
              </div>
            </div>

            <div className="field">
              <label htmlFor="stay-code">Stay code (optional)</label>
              <div className="code-row">
                <input
                  id="stay-code"
                  name="stay-code"
                  autoComplete="off"
                  autoCapitalize="characters"
                  value={codeInput}
                  aria-invalid={codeError ? true : undefined}
                  aria-describedby={codeError ? "stay-code-error" : "stay-code-hint"}
                  onChange={(event) => setCodeInput(event.target.value)}
                />
                <button
                  type="button"
                  className="button button-secondary button-small"
                  disabled={busy}
                  onClick={applyCode}
                >
                  Apply code
                </button>
              </div>
              {codeError === null && (
                <p id="stay-code-hint" className="muted" aria-live="polite">
                  {discounted !== null && discounted.discountPercent > 0
                    ? `Code applied: ${discounted.discountPercent}% off (was ${discounted.basePrice} ${discounted.currency}).`
                    : "Staying at a partner hotel? Your stay code gives a discount."}
                </p>
              )}
              {codeError !== null && (
                <div id="stay-code-error" className="field-error">
                  <p>{codeError}</p>
                  <button type="button" className="button button-small" onClick={continueWithoutCode}>
                    Continue without the code
                  </button>
                </div>
              )}
            </div>

            <div className="field">
              <span id="test-card-label" className="field-label">
                Test card
              </span>
              <p className="muted">
                This is a simulated payment: no real money is charged. Choose how the bank answers.
              </p>
              <div role="radiogroup" aria-labelledby="test-card-label" className="choice-list">
                {TEST_CARDS.map((option) => (
                  <label key={option.token} className="choice">
                    <input
                      type="radio"
                      name="test-card"
                      value={option.token}
                      checked={option.token === card}
                      onChange={() => setCard(option.token)}
                    />
                    <span>{option.label}</span>
                  </label>
                ))}
              </div>
            </div>

            {paymentError !== null && (
              <p role="alert" className="form-error">
                {paymentError}
              </p>
            )}

            <button type="submit" className="button" disabled={busy || codeError !== null}>
              Pay {price} {currency}
            </button>
          </form>
        )}
      </section>
    </main>
  );
}