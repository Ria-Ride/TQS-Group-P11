import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getProfile, login, registerGuest } from "./auth";
import { ApiError } from "./client";

const fetchMock = vi.fn<typeof fetch>();

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const ana = { name: "Ana Sousa", email: "ana.sousa@example.com", password: "Passw0rd!" };
const grant = { userId: "7c9e6679-7425-40de-944b-e07fc1f90ae7", accessToken: "t", tokenType: "Bearer" };

describe("registerGuest", () => {
  it("posts the form as JSON to /api/auth/guest and returns the grant", async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, grant));

    await expect(registerGuest(ana)).resolves.toEqual(grant);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/auth/guest");
    expect(init?.method).toBe("POST");
    expect(init?.headers).toMatchObject({ "Content-Type": "application/json" });
    expect(init?.body).toBe(JSON.stringify(ana));
  });

  it("rejects with the status and the field errors of a 400", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(400, { status: 400, errors: { name: "must not be blank" } }),
    );

    const error = await registerGuest({ ...ana, name: "" }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 400, fieldErrors: { name: "must not be blank" } });
  });

  it("rejects with no field errors when the error body is not JSON", async () => {
    fetchMock.mockResolvedValue(new Response("<html>Bad Gateway</html>", { status: 502 }));

    const error = await registerGuest(ana).catch((e: unknown) => e);

    expect(error).toMatchObject({ status: 502, fieldErrors: {} });
  });
});

describe("login", () => {
  it("posts the credentials to /api/auth/login", async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, grant));

    await expect(login({ email: ana.email, password: ana.password })).resolves.toEqual(grant);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/auth/login");
  });

  it("rejects with status 401 for invalid credentials", async () => {
    fetchMock.mockResolvedValue(jsonResponse(401, { status: 401, errors: {} }));

    const error = await login({ email: ana.email, password: "WrongPass1!" }).catch(
      (e: unknown) => e,
    );

    expect(error).toMatchObject({ status: 401, fieldErrors: {} });
  });
});

describe("getProfile", () => {
  it("gets /api/users/{id} with the token as a bearer header", async () => {
    const profile = { id: grant.userId, name: "Ana Sousa", email: ana.email, role: "END_USER" };
    fetchMock.mockResolvedValue(jsonResponse(200, profile));

    await expect(getProfile(grant.userId, "the-token")).resolves.toEqual(profile);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`/api/users/${grant.userId}`);
    expect(init?.headers).toMatchObject({ Authorization: "Bearer the-token" });
    expect(init?.body).toBeUndefined();
  });
});