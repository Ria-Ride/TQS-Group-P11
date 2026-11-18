export interface Session {
  userId: string;
  accessToken: string;
}

export function saveSession(session: Session): void {
  throw new Error("not implemented yet");
}

export function loadSession(): Session | null {
  throw new Error("not implemented yet");
}

export function clearSession(): void {
  throw new Error("not implemented yet");
}