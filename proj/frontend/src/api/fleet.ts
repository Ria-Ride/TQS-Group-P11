import { requestJson } from "./client";

export type VehicleType = "E_BIKE" | "E_SCOOTER";
export type VehicleState =
  "DEACTIVATED" | "AVAILABLE" | "RESERVED" | "IN_USE" | "MAINTENANCE" | "IN_TRANSIT" | "RETIRED";
export type VehicleAction = "ACTIVATE" | "DEACTIVATE" | "START_MOVE" | "CONFIRM_ARRIVAL" | "RETIRE";

/** A hub and how many vehicles it holds now (BR14). */
export interface Hub {
  id: string;
  code: string;
  name: string;
  latitude: number;
  longitude: number;
  capacity: number;
  vehiclesHeld: number;
}

/** Numbers are null when the field was left empty, so the server reports it as missing. */
export interface NewHub {
  code: string;
  name: string;
  latitude: number | null;
  longitude: number | null;
  capacity: number | null;
}

/** hubId is null while the vehicle is in use, in transit or retired. */
export interface Vehicle {
  id: string;
  code: string;
  type: VehicleType;
  state: VehicleState;
  hubId: string | null;
}

export interface NewVehicle {
  code: string;
  type: VehicleType;
  hubId: string;
}

/** One entry of a vehicle's state history (BR16); from is null for the registration. */
export interface StateChange {
  from: VehicleState | null;
  to: VehicleState;
  at: string;
}

/** US08: hubs and vehicles, for operators (the API answers 403 to roles that may not change them). */
export function listHubs(token: string): Promise<Hub[]> {
  return requestJson<Hub[]>("/hubs", { token });
}

export function registerHub(input: NewHub, token: string): Promise<Hub> {
  return requestJson<Hub>("/hubs", { method: "POST", body: input, token });
}

/** BR14: refused (409) below the vehicles the hub holds. */
export function changeCapacity(
  hubId: string,
  capacity: number | null,
  token: string,
): Promise<Hub> {
  return requestJson<Hub>(`/hubs/${encodeURIComponent(hubId)}`, {
    method: "PATCH",
    body: { capacity },
    token,
  });
}

export function listVehicles(token: string): Promise<Vehicle[]> {
  return requestJson<Vehicle[]>("/vehicles", { token });
}

export function registerVehicle(input: NewVehicle, token: string): Promise<Vehicle> {
  return requestJson<Vehicle>("/vehicles", { method: "POST", body: input, token });
}

/** BR16. hubId is only sent to confirm an arrival: the API refuses it on any other action. */
export function transition(
  vehicleId: string,
  action: VehicleAction,
  token: string,
  hubId?: string,
): Promise<Vehicle> {
  return requestJson<Vehicle>(`/vehicles/${encodeURIComponent(vehicleId)}/transitions`, {
    method: "POST",
    body: hubId === undefined ? { action } : { action, hubId },
    token,
  });
}

export function vehicleHistory(vehicleId: string, token: string): Promise<StateChange[]> {
  return requestJson<StateChange[]>(`/vehicles/${encodeURIComponent(vehicleId)}/history`, {
    token,
  });
}
