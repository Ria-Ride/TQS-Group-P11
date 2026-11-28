import { requestJson } from "./client";

/** A kind of pass (US03). Prices are strings with cents ("10.00"), never floating point. */
export interface PassType {
  id: string;
  code: string;
  name: string;
  price: string;
  currency: string;
  validityDays: number;
  vehicleTypes: string[];
}

/** What a pass would cost the tourist now, with the stay discount when the code is valid. */
export interface PriceQuote {
  passTypeId: string;
  basePrice: string;
  price: string;
  currency: string;
  discountPercent: number;
}

/** stayCode is null for the base price; paymentToken is a test card of the simulated gateway. */
export interface NewPass {
  passTypeId: string;
  stayCode: string | null;
  paymentToken: string;
}

/** An issued pass. validFrom and validTo are ISO instants in UTC (BR8). */
export interface Pass {
  id: string;
  passTypeId: string;
  passTypeCode: string;
  price: string;
  currency: string;
  validFrom: string;
  validTo: string;
}

export function listPassTypes(token: string): Promise<PassType[]> {
  return requestJson<PassType[]>("/pass-types", { token });
}

/** Nothing is charged or reserved: the purchase checks the code again. */
export function quotePrice(
  passTypeId: string,
  stayCode: string | null,
  token: string,
): Promise<PriceQuote> {
  const query = stayCode ? `?${new URLSearchParams({ stayCode })}` : "";
  return requestJson<PriceQuote>(`/pass-types/${passTypeId}/price${query}`, { token });
}

export function buyPass(pass: NewPass, token: string): Promise<Pass> {
  const body: Record<string, string> = {
    passTypeId: pass.passTypeId,
    paymentToken: pass.paymentToken,
  };
  if (pass.stayCode) {
    body.stayCode = pass.stayCode;
  }
  return requestJson<Pass>("/passes", { method: "POST", body, token });
}

/** The tourist's own passes (GET /passes); US05 uses them to bound a booking for later (BR4). */
export function listPasses(token: string): Promise<Pass[]> {
  return requestJson<Pass[]>("/passes", { token });
}
