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
  throw new Error("not implemented yet");
}

export function login(input: Credentials): Promise<AccessGrant> {
  throw new Error("not implemented yet");
}

export function getProfile(userId: string, token: string): Promise<UserProfile> {
  throw new Error("not implemented yet");
}