import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { assignTicket, listOpenTickets, listTechnicians, openTicket } from "./maintenance";

const TICKET_ID = "00000000-0000-0000-0000-000000000501";
const VEHICLE_ID = "3c4d5e6f-0000-4000-8000-000000000009";
const TECHNICIAN_ID = "00000000-0000-0000-0000-000000000301";

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

describe("maintenance API", () => {
  it("lists the open tickets with the operator's token", async () => {
    await listOpenTickets("op-token");

    expect(sentRequest()).toEqual({
      url: "/api/tickets",
      method: "GET",
      body: undefined,
      authorization: "Bearer op-token",
    });
  });

  it("opens a ticket", async () => {
    const input = {
      vehicleId: VEHICLE_ID,
      faultCode: "BROKEN_BASKET",
      description: "Front basket cracked",
    };

    await openTicket(input, "op-token");

    expect(sentRequest()).toEqual({
      url: "/api/tickets",
      method: "POST",
      body: input,
      authorization: "Bearer op-token",
    });
  });

  it("assigns a ticket to a technician", async () => {
    await assignTicket(TICKET_ID, TECHNICIAN_ID, "op-token");

    expect(sentRequest()).toEqual({
      url: `/api/tickets/${TICKET_ID}/assignee`,
      method: "PATCH",
      body: { technicianId: TECHNICIAN_ID },
      authorization: "Bearer op-token",
    });
  });

  it("lists the field technicians", async () => {
    await listTechnicians("op-token");

    expect(sentRequest()).toEqual({
      url: "/api/technicians",
      method: "GET",
      body: undefined,
      authorization: "Bearer op-token",
    });
  });
});
