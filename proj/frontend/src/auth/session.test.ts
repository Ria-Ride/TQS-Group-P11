import { describe, expect, it } from "vitest";
import { clearSession, loadSession, saveSession } from "./session";

const session = { userId: "7c9e6679-7425-40de-944b-e07fc1f90ae7", accessToken: "t" };

describe("session", () => {
  it("returns the saved session", () => {
    saveSession(session);

    expect(loadSession()).toEqual(session);
  });

  it("returns null when nothing was saved", () => {
    expect(loadSession()).toBeNull();
  });

  it("returns null after clearing", () => {
    saveSession(session);
    clearSession();

    expect(loadSession()).toBeNull();
  });

  it("returns null for corrupted storage", () => {
    sessionStorage.setItem("riaride.session", "{not json");

    expect(loadSession()).toBeNull();
  });

  it("returns null for stored data with another shape", () => {
    sessionStorage.setItem("riaride.session", JSON.stringify({ userId: 42 }));

    expect(loadSession()).toBeNull();
  });
});