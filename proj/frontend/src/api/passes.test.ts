import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { buyPass, listPassTypes, quotePrice } from "./passes";

const TOKEN = "token-for-ana";
const DAY_ID = "7a1e0000-0000-4000-8000-000000000001";

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

describe("passes API", () => {
  it("lists the pass types with the token", async () => {
    await listPassTypes(TOKEN);

    expect(sentRequest()).toMatchObject({
      url: "/api/pass-types",
      method: "GET",
      authorization: `Bearer ${TOKEN}`,
    });
  });

  it("asks the price without a stay code", async () => {
    await quotePrice(DAY_ID, null, TOKEN);

    expect(sentRequest().url).toBe(`/api/pass-types/${DAY_ID}/price`);
  });

  it("asks the price with the stay code encoded in the query", async () => {
    await quotePrice(DAY_ID, "RIA 1&2", TOKEN);

    expect(sentRequest().url).toBe(`/api/pass-types/${DAY_ID}/price?stayCode=RIA+1%262`);
  });

  it("buys with the pass type, the stay code and the test card", async () => {
    await buyPass({ passTypeId: DAY_ID, stayCode: "RIA-DEMO-01", paymentToken: "tok_approve" }, TOKEN);

    expect(sentRequest()).toMatchObject({
      url: "/api/passes",
      method: "POST",
      authorization: `Bearer ${TOKEN}`,
      body: { passTypeId: DAY_ID, stayCode: "RIA-DEMO-01", paymentToken: "tok_approve" },
    });
  });

  it("leaves the stay code out when there is none, for the base price", async () => {
    await buyPass({ passTypeId: DAY_ID, stayCode: null, paymentToken: "tok_decline" }, TOKEN);

    expect(sentRequest().body).toEqual({ passTypeId: DAY_ID, paymentToken: "tok_decline" });
  });
});
