import { useEffect, useState } from "react";
import { changeRules, type RulesOfUse } from "../api/backoffice";
import { ApiError, type FieldErrors } from "../api/client";
import type { VehicleType } from "../api/fleet";
import {
  RULES_CHANGED_BY_SOMEONE_ELSE,
  RULES_MESSAGES,
} from "../pages/backofficeMessages";
import { parseNumber, TYPE_LABELS, VEHICLE_TYPES } from "../pages/fleetLabels";
import { friendlyFieldErrors, GENERIC_ERROR } from "../pages/messages";
import { TextField } from "./TextField";

interface RulesOfUseFormProps {
  rules: RulesOfUse;
  versions: RulesOfUse[];
  token: string;
  onSaved: () => void;
}

interface Values {
  maxBookingMinutes: string;
  minBatteryPercent: string;
  startWindowMinutes: string;
  repositioningWindowMinutes: string;
  overtimeGraceMinutes: string;
  overtimeBlockMinutes: string;
  overtimeRatePerBlock: string;
  rates: Record<VehicleType, string>;
}

function valuesOf(rules: RulesOfUse): Values {
  const rates = { E_BIKE: "", E_SCOOTER: "" } as Record<VehicleType, string>;
  for (const type of rules.vehicleTypes) {
    rates[type.type] = String(type.batteryPercentPer30Minutes);
  }
  return {
    maxBookingMinutes: String(rules.maxBookingMinutes),
    minBatteryPercent: String(rules.minBatteryPercent),
    startWindowMinutes: String(rules.startWindowMinutes),
    repositioningWindowMinutes: String(rules.repositioningWindowMinutes),
    overtimeGraceMinutes: String(rules.overtimeGraceMinutes),
    overtimeBlockMinutes: String(rules.overtimeBlockMinutes),
    overtimeRatePerBlock: rules.overtimeRatePerBlock,
    rates,
  };
}

function describe(rules: RulesOfUse): string {
  return `Version ${rules.version}, ${new Date(rules.createdAt).toLocaleString()}`;
}

/**
 * US14: the client manager reads the rules in force and saves new values. Every save makes the
 * next version and leaves the old ones as they were, so a change only applies to bookings made
 * afterwards (BR21). The deposits have their own table.
 */
export function RulesOfUseForm({ rules, versions, token, onSaved }: RulesOfUseFormProps) {
  const [values, setValues] = useState<Values>(() => valuesOf(rules));
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saved, setSaved] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  // Show the saved values again when the rules are reloaded.
  useEffect(() => setValues(valuesOf(rules)), [rules]);

  function set(field: keyof Omit<Values, "rates">, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
  }

  async function submit() {
    setBusy(true);
    setFieldErrors({});
    setFormError(null);
    setSaved(null);
    try {
      const result = await changeRules(
        {
          maxBookingMinutes: parseNumber(values.maxBookingMinutes),
          minBatteryPercent: parseNumber(values.minBatteryPercent),
          startWindowMinutes: parseNumber(values.startWindowMinutes),
          repositioningWindowMinutes: parseNumber(values.repositioningWindowMinutes),
          overtimeGraceMinutes: parseNumber(values.overtimeGraceMinutes),
          overtimeBlockMinutes: parseNumber(values.overtimeBlockMinutes),
          overtimeRatePerBlock: values.overtimeRatePerBlock.trim(),
          batteryPercentPer30Minutes: {
            E_BIKE: parseNumber(values.rates.E_BIKE),
            E_SCOOTER: parseNumber(values.rates.E_SCOOTER),
          },
        },
        token,
      );
      setSaved(result.version);
      onSaved();
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setFieldErrors(friendlyFieldErrors(error.fieldErrors, RULES_MESSAGES));
      } else if (error instanceof ApiError && error.status === 409) {
        setFormError(RULES_CHANGED_BY_SOMEONE_ELSE);
      } else {
        setFormError(GENERIC_ERROR);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <p>{describe(rules)}.</p>
      <form
        className="form"
        aria-label="Change the rules of use"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <TextField
          id="rules-max-booking"
          label="Longest booking (minutes)"
          inputMode="numeric"
          value={values.maxBookingMinutes}
          onChange={(value) => set("maxBookingMinutes", value)}
          error={fieldErrors.maxBookingMinutes}
        />
        <TextField
          id="rules-min-battery"
          label="Minimum battery (%)"
          inputMode="numeric"
          value={values.minBatteryPercent}
          onChange={(value) => set("minBatteryPercent", value)}
          error={fieldErrors.minBatteryPercent}
        />
        <TextField
          id="rules-start-window"
          label="Start window (minutes)"
          inputMode="numeric"
          value={values.startWindowMinutes}
          onChange={(value) => set("startWindowMinutes", value)}
          error={fieldErrors.startWindowMinutes}
        />
        <TextField
          id="rules-repositioning"
          label="Repositioning window (minutes)"
          inputMode="numeric"
          value={values.repositioningWindowMinutes}
          onChange={(value) => set("repositioningWindowMinutes", value)}
          error={fieldErrors.repositioningWindowMinutes}
        />
        <TextField
          id="rules-overtime-grace"
          label="Overtime grace (minutes)"
          inputMode="numeric"
          value={values.overtimeGraceMinutes}
          onChange={(value) => set("overtimeGraceMinutes", value)}
          error={fieldErrors.overtimeGraceMinutes}
        />
        <TextField
          id="rules-overtime-block"
          label="Overtime block (minutes)"
          inputMode="numeric"
          value={values.overtimeBlockMinutes}
          onChange={(value) => set("overtimeBlockMinutes", value)}
          error={fieldErrors.overtimeBlockMinutes}
        />
        <TextField
          id="rules-overtime-rate"
          label="Overtime price per block (EUR)"
          inputMode="decimal"
          value={values.overtimeRatePerBlock}
          onChange={(value) => set("overtimeRatePerBlock", value)}
          error={fieldErrors.overtimeRatePerBlock}
        />
        {VEHICLE_TYPES.map((type) => (
          <TextField
            key={type}
            id={`rules-battery-${type}`}
            label={`${TYPE_LABELS[type]} battery per 30 minutes (%)`}
            inputMode="numeric"
            value={values.rates[type]}
            onChange={(value) =>
              setValues((current) => ({ ...current, rates: { ...current.rates, [type]: value } }))
            }
          />
        ))}
        {fieldErrors.batteryPercentPer30Minutes && (
          <p className="field-error">{fieldErrors.batteryPercentPer30Minutes}</p>
        )}
        {formError && (
          <p role="alert" className="form-error">
            {formError}
          </p>
        )}
        {saved !== null && <p role="status">Saved as version {saved}.</p>}
        <button type="submit" className="button" disabled={busy}>
          Save as a new version
        </button>
      </form>

      <h3>Versions</h3>
      <ol className="history" aria-label="Versions of the rules">
        {versions.map((version) => (
          <li key={version.version}>
            {describe(version)}, longest booking {version.maxBookingMinutes} minutes, minimum
            battery {version.minBatteryPercent}%
          </li>
        ))}
      </ol>
    </>
  );
}
