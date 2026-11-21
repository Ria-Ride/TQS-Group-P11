import { requestJson } from "./client";

export type Role = "END_USER" | "OPERATOR" | "CLIENT_MANAGER" | "ADMIN";

/** An account as the admin sees it (never with a password). */
export interface Account {
  id: string;
  name: string;
  email: string;
  role: Role;
  fieldTechnician: boolean;
  active: boolean;
}

export interface NewAccount {
  name: string;
  email: string;
  password: string;
  role: Role;
  fieldTechnician: boolean;
}

/** US13: account management, for admins only (the API answers 403 to anyone else). */
export function listAccounts(token: string): Promise<Account[]> {
  return requestJson<Account[]>("/admin/users", { token });
}

export function createAccount(input: NewAccount, token: string): Promise<Account> {
  return requestJson<Account>("/admin/users", { method: "POST", body: input, token });
}

export function changeRole(
  id: string,
  role: Role,
  fieldTechnician: boolean,
  token: string,
): Promise<Account> {
  return requestJson<Account>(`/admin/users/${encodeURIComponent(id)}/role`, {
    method: "PATCH",
    body: { role, fieldTechnician },
    token,
  });
}

export function setActive(id: string, active: boolean, token: string): Promise<Account> {
  return requestJson<Account>(`/admin/users/${encodeURIComponent(id)}/status`, {
    method: "PATCH",
    body: { active },
    token,
  });
}