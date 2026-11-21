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

const NOT_IMPLEMENTED = "Not implemented yet (KAN-85)";

export function listHubs(_token: string): Promise<Hub[]> {
  throw new Error(NOT_IMPLEMENTED);
}

export function registerHub(_input: NewHub, _token: string): Promise<Hub> {
  throw new Error(NOT_IMPLEMENTED);
}

export function changeCapacity(
  _hubId: string,
  _capacity: number | null,
  _token: string,
): Promise<Hub> {
  throw new Error(NOT_IMPLEMENTED);
}

export function listVehicles(_token: string): Promise<Vehicle[]> {
  throw new Error(NOT_IMPLEMENTED);
}

export function registerVehicle(_input: NewVehicle, _token: string): Promise<Vehicle> {
  throw new Error(NOT_IMPLEMENTED);
}

export function transition(
  _vehicleId: string,
  _action: VehicleAction,
  _token: string,
  _hubId?: string,
): Promise<Vehicle> {
  throw new Error(NOT_IMPLEMENTED);
}

export function vehicleHistory(_vehicleId: string, _token: string): Promise<StateChange[]> {
  throw new Error(NOT_IMPLEMENTED);
}
