import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { changeRole, createAccount, listAccounts, setActive, type Account } from "./admin";

const rui: Account = {
  id: "4b1d6f2e-8a3c-4e5b-9d7f-1a2b3c4d5e6f",
  name: "Rui Costa",
  email: "rui.costa@example.com",
  role: "OPERATOR",
  fieldTechnician: false,
  active: true,
};

let fetchMock: Mock;

beforeEach(() => {
  fetchMock = vi.fn().mockResolvedValue(
    new Response(JSON.stringify(rui), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
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

describe("admin API", () => {
  it("lists the accounts with the admin's token", async () => {
    await listAccounts("admin-token");

    expect(sentRequest()).toEqual({
      url: "/api/admin/users",
      method: "GET",
      body: undefined,
      authorization: "Bearer admin-token",
    });
  });

  it("creates an account", async () => {
    const input = {
      name: "Rui Costa",
      email: "rui.costa@example.com",
      password: "Passw0rd!",
      role: "OPERATOR" as const,
      fieldTechnician: true,
    };

    await createAccount(input, "admin-token");

    expect(sentRequest()).toEqual({
      url: "/api/admin/users",
      method: "POST",
      body: input,
      authorization: "Bearer admin-token",
    });
  });

  it("changes the role and the technician flag", async () => {
    await changeRole(rui.id, "CLIENT_MANAGER", false, "admin-token");

    expect(sentRequest()).toEqual({
      url: `/api/admin/users/${rui.id}/role`,
      method: "PATCH",
      body: { role: "CLIENT_MANAGER", fieldTechnician: false },
      authorization: "Bearer admin-token",
    });
  });

  it("activates or deactivates an account", async () => {
    await setActive(rui.id, false, "admin-token");

    expect(sentRequest()).toEqual({
      url: `/api/admin/users/${rui.id}/status`,
      method: "PATCH",
      body: { active: false },
      authorization: "Bearer admin-token",
    });
  });
});