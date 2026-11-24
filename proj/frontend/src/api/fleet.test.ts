import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import {
  changeCapacity,
  listHubs,
  listVehicles,
  registerHub,
  registerVehicle,
  searchHubVehicles,
  transition,
  vehicleHistory,
} from "./fleet";

const HUB_ID = "2b3c4d5e-0000-4000-8000-000000000103";
const VEHICLE_ID = "3c4d5e6f-0000-4000-8000-000000000009";

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

describe("fleet API", () => {
  it("lists the hubs with the operator's token", async () => {
    await listHubs("op-token");

    expect(sentRequest()).toEqual({
      url: "/api/hubs",
      method: "GET",
      body: undefined,
      authorization: "Bearer op-token",
    });
  });

  it("registers a hub", async () => {
    const input = {
      code: "hub-03",
      name: "Canal Central",
      latitude: 40.6405,
      longitude: -8.6538,
      capacity: 10,
    };

    await registerHub(input, "op-token");

    expect(sentRequest()).toEqual({
      url: "/api/hubs",
      method: "POST",
      body: input,
      authorization: "Bearer op-token",
    });
  });

  it("changes the capacity of a hub", async () => {
    await changeCapacity(HUB_ID, 9, "op-token");

    expect(sentRequest()).toEqual({
      url: `/api/hubs/${HUB_ID}`,
      method: "PATCH",
      body: { capacity: 9 },
      authorization: "Bearer op-token",
    });
  });

  it("lists the vehicles", async () => {
    await listVehicles("op-token");

    expect(sentRequest()).toEqual({
      url: "/api/vehicles",
      method: "GET",
      body: undefined,
      authorization: "Bearer op-token",
    });
  });

  it("registers a vehicle at a hub", async () => {
    const input = { code: "bike-0009", type: "E_BIKE" as const, hubId: HUB_ID };

    await registerVehicle(input, "op-token");

    expect(sentRequest()).toEqual({
      url: "/api/vehicles",
      method: "POST",
      body: input,
      authorization: "Bearer op-token",
    });
  });

  it("sends a transition without a hub, as the API refuses one except on arrival", async () => {
    await transition(VEHICLE_ID, "ACTIVATE", "op-token");

    expect(sentRequest()).toEqual({
      url: `/api/vehicles/${VEHICLE_ID}/transitions`,
      method: "POST",
      body: { action: "ACTIVATE" },
      authorization: "Bearer op-token",
    });
  });

  it("sends the destination hub when confirming an arrival", async () => {
    await transition(VEHICLE_ID, "CONFIRM_ARRIVAL", "op-token", HUB_ID);

    expect(sentRequest()).toEqual({
      url: `/api/vehicles/${VEHICLE_ID}/transitions`,
      method: "POST",
      body: { action: "CONFIRM_ARRIVAL", hubId: HUB_ID },
      authorization: "Bearer op-token",
    });
  });

  it("reads the state history of a vehicle", async () => {
    await vehicleHistory(VEHICLE_ID, "op-token");

    expect(sentRequest()).toEqual({
      url: `/api/vehicles/${VEHICLE_ID}/history`,
      method: "GET",
      body: undefined,
      authorization: "Bearer op-token",
    });
  });

  // ------------------------------------------------------------- US02: search a hub

  it("searches the vehicles of a hub with no filter", async () => {
    await searchHubVehicles(HUB_ID, { type: null, minutes: null }, "tourist-token");

    expect(sentRequest()).toEqual({
      url: `/api/hubs/${HUB_ID}/vehicles`,
      method: "GET",
      body: undefined,
      authorization: "Bearer tourist-token",
    });
  });

  it("sends the type and the length of the ride as query parameters", async () => {
    await searchHubVehicles(HUB_ID, { type: "E_SCOOTER", minutes: 60 }, "tourist-token");

    expect(sentRequest().url).toBe(`/api/hubs/${HUB_ID}/vehicles?type=E_SCOOTER&minutes=60`);
  });

  it("leaves out the filter that is not set", async () => {
    await searchHubVehicles(HUB_ID, { type: null, minutes: 30 }, "tourist-token");

    expect(sentRequest().url).toBe(`/api/hubs/${HUB_ID}/vehicles?minutes=30`);
  });
});
