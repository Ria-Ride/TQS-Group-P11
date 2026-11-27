import { requestJson } from "./client";
import type { VehicleType } from "./fleet";
import type { PassType } from "./passes";

/** What the rules of use say about one vehicle type. The deposit is a string with cents. */
export interface VehicleTypeRules {
  type: VehicleType;
  depositAmount: string;
  currency: string;
  batteryPercentPer30Minutes: number;
}

/** A version of the rules of use (US14, BR21). createdBy is null for the first, seeded version. */
export interface RulesOfUse {
  version: number;
  createdAt: string;
  createdBy: string | null;
  maxBookingMinutes: number;
  minBatteryPercent: number;
  startWindowMinutes: number;
  repositioningWindowMinutes: number;
  overtimeGraceMinutes: number;
  overtimeBlockMinutes: number;
  overtimeRatePerBlock: string;
  currency: string;
  vehicleTypes: VehicleTypeRules[];
}

/** Numbers are null when the field was left empty, so the server reports it as missing. */
export interface RulesInput {
  maxBookingMinutes: number | null;
  minBatteryPercent: number | null;
  startWindowMinutes: number | null;
  repositioningWindowMinutes: number | null;
  overtimeGraceMinutes: number | null;
  overtimeBlockMinutes: number | null;
  overtimeRatePerBlock: string;
  batteryPercentPer30Minutes: Record<VehicleType, number | null>;
}

export interface NewPassType {
  code: string;
  name: string;
  validityDays: number | null;
  price: string;
  vehicleTypes: VehicleType[];
}

/** Only the fields that change are sent. */
export interface PassTypeChange {
  name?: string;
  validityDays?: number | null;
  price?: string;
  vehicleTypes?: VehicleType[];
}

export interface Hotel {
  id: string;
  name: string;
  discountPercent: number;
}

export interface NewHotel {
  name: string;
  discountPercent: number | null;
}

/** A stay code with its hotel and days; redeemed tells whether a tourist used it (BR9). */
export interface StayCode {
  id: string;
  code: string;
  accommodationId: string;
  accommodationName: string;
  discountPercent: number;
  checkIn: string;
  checkOut: string;
  redeemed: boolean;
}

export interface NewStayCode {
  accommodationId: string;
  checkIn: string;
  checkOut: string;
}

/** US14: what the client manager edits. The API answers 403 to every other role (BR23). */
export function getRules(token: string): Promise<RulesOfUse> {
  return requestJson<RulesOfUse>("/usage-rules", { token });
}

/** Every version, newest first. */
export function listRulesVersions(token: string): Promise<RulesOfUse[]> {
  return requestJson<RulesOfUse[]>("/usage-rules/versions", { token });
}

/** BR21: makes the next version; refused (400) for a value outside its limits. */
export function changeRules(input: RulesInput, token: string): Promise<RulesOfUse> {
  return requestJson<RulesOfUse>("/usage-rules", { method: "PUT", body: input, token });
}

/** BR4: the deposit held for a booking of this vehicle type, as the next version of the rules. */
export function changeDeposit(
  type: VehicleType,
  depositAmount: string,
  token: string,
): Promise<VehicleTypeRules> {
  return requestJson<VehicleTypeRules>(`/vehicle-types/${type}`, {
    method: "PATCH",
    body: { depositAmount },
    token,
  });
}

export function createPassType(input: NewPassType, token: string): Promise<PassType> {
  return requestJson<PassType>("/pass-types", { method: "POST", body: input, token });
}

export function updatePassType(
  id: string,
  change: PassTypeChange,
  token: string,
): Promise<PassType> {
  return requestJson<PassType>(`/pass-types/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: change,
    token,
  });
}

export function listHotels(token: string): Promise<Hotel[]> {
  return requestJson<Hotel[]>("/partner-accommodations", { token });
}

export function registerHotel(input: NewHotel, token: string): Promise<Hotel> {
  return requestJson<Hotel>("/partner-accommodations", { method: "POST", body: input, token });
}

/** Every stay code, newest check-in first. */
export function listStayCodes(token: string): Promise<StayCode[]> {
  return requestJson<StayCode[]>("/stay-codes", { token });
}

/** BR9: the platform makes the code. */
export function issueStayCode(input: NewStayCode, token: string): Promise<StayCode> {
  return requestJson<StayCode>("/stay-codes", { method: "POST", body: input, token });
}
