import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import {
  changeDeposit,
  changeRules,
  createPassType,
  getRules,
  issueStayCode,
  listHotels,
  listRulesVersions,
  listStayCodes,
  registerHotel,
  updatePassType,
  type RulesInput,
} from "./backoffice";

const HOTEL_ID = "5a7e0000-0000-4000-8000-000000000001";
const PASS_ID = "7a1e0000-0000-4000-8000-000000000001";

let fetchMock: Mock;

beforeEach(() => {
  // A new Response for every call: a body can be read only once.
  fetchMock = vi
    .fn()
    .mockImplementation(() =>
      Promise.resolve(
        new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } }),
      ),
    );
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

/** What the page sent: URL, method, JSON body and the Authorization header. */
function sentRequest() {
  expect(fetchMock).toHaveBeenCalledOnce();
  const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
  const headers = init.headers as Record<string, string>;
  return {
    url,
    method: init.method,
    body: typeof init.body === "string" ? JSON.parse(init.body) : undefined,
    authorization: headers.Authorization,
  };
}

describe("client manager API", () => {
  // ------------------------------------------------------------- rules of use (BR21)

  it("reads the rules in force and every version", async () => {
    await getRules("cm-token");
    expect(sentRequest()).toEqual({
      url: "/api/usage-rules",
      method: "GET",
      body: undefined,
      authorization: "Bearer cm-token",
    });

    fetchMock.mockClear();
    await listRulesVersions("cm-token");
    expect(sentRequest().url).toBe("/api/usage-rules/versions");
  });

  it("changes the rules with PUT, sending the numbers and the money as they are", async () => {
    const input: RulesInput = {
      maxBookingMinutes: 240,
      minBatteryPercent: 25,
      startWindowMinutes: 20,
      repositioningWindowMinutes: 12,
      overtimeGraceMinutes: 0,
      overtimeBlockMinutes: 30,
      overtimeRatePerBlock: "2.00",
      batteryPercentPer30Minutes: { E_BIKE: 4, E_SCOOTER: 9 },
    };

    await changeRules(input, "cm-token");

    expect(sentRequest()).toEqual({
      url: "/api/usage-rules",
      method: "PUT",
      body: input,
      authorization: "Bearer cm-token",
    });
  });

  it("changes a deposit with PATCH on the vehicle type", async () => {
    await changeDeposit("E_SCOOTER", "45.00", "cm-token");

    expect(sentRequest()).toEqual({
      url: "/api/vehicle-types/E_SCOOTER",
      method: "PATCH",
      body: { depositAmount: "45.00" },
      authorization: "Bearer cm-token",
    });
  });

  // ------------------------------------------------------------- pass types (BR8)

  it("creates a pass type", async () => {
    const input = {
      code: "WEEK",
      name: "Week pass",
      validityDays: 7,
      price: "40.00",
      vehicleTypes: ["E_BIKE" as const],
    };

    await createPassType(input, "cm-token");

    expect(sentRequest()).toEqual({
      url: "/api/pass-types",
      method: "POST",
      body: input,
      authorization: "Bearer cm-token",
    });
  });

  it("changes the given fields of a pass type", async () => {
    await updatePassType(PASS_ID, { price: "12.50" }, "cm-token");

    expect(sentRequest()).toEqual({
      url: `/api/pass-types/${PASS_ID}`,
      method: "PATCH",
      body: { price: "12.50" },
      authorization: "Bearer cm-token",
    });
  });

  // ------------------------------------------------------------- hotels and stay codes (BR9)

  it("lists and registers partner hotels", async () => {
    await listHotels("cm-token");
    expect(sentRequest().url).toBe("/api/partner-accommodations");

    fetchMock.mockClear();
    await registerHotel({ name: "Hotel Laguna", discountPercent: 15 }, "cm-token");
    expect(sentRequest()).toEqual({
      url: "/api/partner-accommodations",
      method: "POST",
      body: { name: "Hotel Laguna", discountPercent: 15 },
      authorization: "Bearer cm-token",
    });
  });

  it("lists and issues stay codes", async () => {
    await listStayCodes("cm-token");
    expect(sentRequest().url).toBe("/api/stay-codes");

    fetchMock.mockClear();
    const input = { accommodationId: HOTEL_ID, checkIn: "2026-12-01", checkOut: "2026-12-05" };
    await issueStayCode(input, "cm-token");
    expect(sentRequest()).toEqual({
      url: "/api/stay-codes",
      method: "POST",
      body: input,
      authorization: "Bearer cm-token",
    });
  });
});
