interface TextFieldProps {
  id: string;
  label: string;
  type?: "text" | "email" | "password" | "date";
  autoComplete?: string;
  /** Which keyboard phones show, e.g. "decimal" for coordinates. */
  inputMode?: React.HTMLAttributes<HTMLInputElement>["inputMode"];
  value: string;
  onChange: (value: string) => void;
  error?: string;
  children?: React.ReactNode;
}

/** A labelled input whose error, if any, is linked to it so screen readers announce it. */
export function TextField({
  id,
  label,
  type = "text",
  autoComplete,
  inputMode,
  value,
  onChange,
  error,
  children,
}: TextFieldProps) {
  const errorId = `${id}-error`;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        name={id}
        type={type}
        autoComplete={autoComplete}
        inputMode={inputMode}
        value={value}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      {error && (
        <p id={errorId} className="field-error">
          {error} {children}
        </p>
      )}
    </div>
  );
}