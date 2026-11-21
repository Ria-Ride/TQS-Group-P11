/** Kept in sessionStorage: it lasts while the tab is open. Readable by scripts, so XSS matters. */
const STORAGE_KEY = "riaride.session";

export interface Session {
  userId: string;
  accessToken: string;
}

export function saveSession(session: Session): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function loadSession(): Session | null {
  const stored = sessionStorage.getItem(STORAGE_KEY);
  if (stored === null) {
    return null;
  }
  try {
    const value: unknown = JSON.parse(stored);
    return isSession(value) ? value : null;
  } catch {
    return null;
  }
}

export function clearSession(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}

function isSession(value: unknown): value is Session {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return typeof candidate.userId === "string" && typeof candidate.accessToken === "string";
}