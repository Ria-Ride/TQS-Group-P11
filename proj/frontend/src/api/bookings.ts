import { requestJson } from "./client";
import type { VehicleType } from "./fleet";

/** A vehicle type and the deposit a ride on it holds (BR11), with cents ("30.00"). */
export interface VehicleTypeDeposit {
  type: VehicleType;
  deposit: string;
  currency: string;
}

/** plannedEnd is an ISO instant in UTC; paymentToken is a test card of the simulated gateway. */
export interface NewRide {
  vehicleId: string;
  plannedEnd: string;
  paymentToken: string;
}

export type BookingState = "RESERVED" | "ACTIVE" | "COMPLETED" | "CANCELLED" | "EXPIRED";

/** A booking (US04). Times are ISO instants in UTC; startedAt is null until it is started. */
export interface Booking {
  id: string;
  state: BookingState;
  vehicleId: string;
  passId: string;
  startHubId: string;
  plannedStart: string;
  plannedEnd: string;
  startedAt: string | null;
  deposit: string;
  currency: string;
}

/** The deposit of each vehicle type, from the rules of use in force. */
export function listVehicleTypes(token: string): Promise<VehicleTypeDeposit[]> {
  return requestJson<VehicleTypeDeposit[]>("/vehicle-types", { token });
}

/**
 * US04: starts using the vehicle now. The deposit is held first (BR11); the API refuses with the
 * rule that fails (400, 402, 409, 504) and then nothing is started.
 */
export function startRide(ride: NewRide, token: string): Promise<Booking> {
  return requestJson<Booking>("/bookings", { method: "POST", body: ride, token });
}