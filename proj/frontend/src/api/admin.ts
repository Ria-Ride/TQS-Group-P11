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

// US13 red step: not implemented yet. requestJson is imported for the green step.
void requestJson;

export function listAccounts(_token: string): Promise<Account[]> {
  return Promise.resolve([]);
}

export function createAccount(_input: NewAccount, _token: string): Promise<Account> {
  return Promise.resolve({} as Account);
}

export function changeRole(
  _id: string,
  _role: Role,
  _fieldTechnician: boolean,
  _token: string,
): Promise<Account> {
  return Promise.resolve({} as Account);
}

export function setActive(_id: string, _active: boolean, _token: string): Promise<Account> {
  return Promise.resolve({} as Account);
}