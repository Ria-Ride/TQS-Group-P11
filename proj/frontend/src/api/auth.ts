import { requestJson } from "./client";

export interface AccessGrant {
  userId: string;
  accessToken: string;
  tokenType: string;
}

export interface UserProfile {
  id: string;
  name: string;
  email: string;
  role: string;
  /** US11: true for an OPERATOR who is a field technician; the server always sends it. */
  fieldTechnician?: boolean;
}

export interface GuestRegistration {
  name: string;
  email: string;
  password: string;
}

export interface Credentials {
  email: string;
  password: string;
}

export function registerGuest(input: GuestRegistration): Promise<AccessGrant> {
  return requestJson<AccessGrant>("/auth/guest", { method: "POST", body: input });
}

export function login(input: Credentials): Promise<AccessGrant> {
  return requestJson<AccessGrant>("/auth/login", { method: "POST", body: input });
}

export function getProfile(userId: string, token: string): Promise<UserProfile> {
  return requestJson<UserProfile>(`/users/${encodeURIComponent(userId)}`, { token });
}