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

/** US05: a booking for a later period; plannedStart must be in the future. */
export interface NewReservation extends NewRide {
  plannedStart: string;
}

export type BookingState = "RESERVED" | "ACTIVE" | "COMPLETED" | "CANCELLED" | "EXPIRED";

/**
 * A booking (US04, US05). Times are ISO instants in UTC. startedAt is null until it is started;
 * startWindowEndsAt is the last moment a Reserved booking can be started (BR7), null otherwise;
 * cancelReason is set only when Cancelled.
 */
export interface Booking {
  id: string;
  state: BookingState;
  vehicleId: string;
  passId: string;
  startHubId: string;
  plannedStart: string;
  plannedEnd: string;
  startWindowEndsAt: string | null;
  startedAt: string | null;
  cancelReason: string | null;
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

/**
 * US05: books the vehicle for a later period. The vehicle and the booking are Reserved and the
 * deposit is held (BR11) until the booking is started, cancelled or expires (BR7).
 */
export function bookForLater(reservation: NewReservation, token: string): Promise<Booking> {
  return requestJson<Booking>("/bookings", { method: "POST", body: reservation, token });
}

/** The tourist's bookings, latest planned start first. */
export function listBookings(token: string): Promise<Booking[]> {
  return requestJson<Booking[]>("/bookings", { token });
}

/** BR7: starts a Reserved booking inside its window; the vehicle is then unlocked. */
export function startBooking(id: string, token: string): Promise<Booking> {
  return requestJson<Booking>(`/bookings/${encodeURIComponent(id)}/start`, {
    method: "POST",
    body: {},
    token,
  });
}

/** BR13: cancels a Reserved booking; the whole deposit is given back. */
export function cancelBooking(id: string, token: string): Promise<Booking> {
  return requestJson<Booking>(`/bookings/${encodeURIComponent(id)}/cancel`, {
    method: "POST",
    body: {},
    token,
  });
}
