import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import {
  bookForLater,
  cancelBooking,
  listBookings,
  listVehicleTypes,
  startBooking,
  startRide,
} from "./bookings";

const TOKEN = "token-for-ana";

let fetchMock: Mock;

beforeEach(() => {
  fetchMock = vi
    .fn()
    .mockResolvedValue(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } }),
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

describe("bookings API", () => {
  it("starts a ride with the vehicle, the planned end and the test card", async () => {
    await startRide(
      { vehicleId: "v-1", plannedEnd: "2026-11-26T11:00:00.000Z", paymentToken: "tok_approve" },
      TOKEN,
    );

    expect(sentRequest()).toMatchObject({
      url: "/api/bookings",
      method: "POST",
      authorization: `Bearer ${TOKEN}`,
      body: {
        vehicleId: "v-1",
        plannedEnd: "2026-11-26T11:00:00.000Z",
        paymentToken: "tok_approve",
      },
    });
  });

  it("lists the vehicle types with their deposit", async () => {
    await listVehicleTypes(TOKEN);

    expect(sentRequest()).toMatchObject({
      url: "/api/vehicle-types",
      method: "GET",
      authorization: `Bearer ${TOKEN}`,
    });
  });

  it("books a vehicle for later with the planned start and end (US05)", async () => {
    await bookForLater(
      {
        vehicleId: "v-1",
        plannedStart: "2026-11-26T14:00:00.000Z",
        plannedEnd: "2026-11-26T15:00:00.000Z",
        paymentToken: "tok_approve",
      },
      TOKEN,
    );

    expect(sentRequest()).toMatchObject({
      url: "/api/bookings",
      method: "POST",
      authorization: `Bearer ${TOKEN}`,
      body: {
        vehicleId: "v-1",
        plannedStart: "2026-11-26T14:00:00.000Z",
        plannedEnd: "2026-11-26T15:00:00.000Z",
        paymentToken: "tok_approve",
      },
    });
  });

  it("lists the tourist's bookings", async () => {
    await listBookings(TOKEN);

    expect(sentRequest()).toMatchObject({
      url: "/api/bookings",
      method: "GET",
      authorization: `Bearer ${TOKEN}`,
    });
  });

  it("starts a reserved booking", async () => {
    await startBooking("booking-1", TOKEN);

    expect(sentRequest()).toMatchObject({
      url: "/api/bookings/booking-1/start",
      method: "POST",
      authorization: `Bearer ${TOKEN}`,
    });
  });

  it("cancels a reserved booking", async () => {
    await cancelBooking("booking-1", TOKEN);

    expect(sentRequest()).toMatchObject({
      url: "/api/bookings/booking-1/cancel",
      method: "POST",
      authorization: `Bearer ${TOKEN}`,
    });
  });
});
