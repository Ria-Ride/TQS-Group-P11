import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { getProfile } from "../api/auth";
import { ApiError } from "../api/client";
import { loadSession, saveSession } from "../auth/session";
import { renderApp } from "../test/renderApp";

vi.mock("../api/auth");

const session = { userId: "7c9e6679-7425-40de-944b-e07fc1f90ae7", accessToken: "token-for-ana" };

describe("home page", () => {
  it("sends a visitor without a session to the login", () => {
    renderApp("/");

    expect(screen.getByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(getProfile).not.toHaveBeenCalled();
  });

  it("sends an unknown address to the login when there is no session", () => {
    renderApp("/no-such-page");

    expect(screen.getByRole("heading", { name: "Log in" })).toBeInTheDocument();
  });

  it("greets the tourist with the profile loaded with their token", async () => {
    saveSession(session);
    vi.mocked(getProfile).mockResolvedValue({
      id: session.userId,
      name: "Ana Sousa",
      email: "ana.sousa@example.com",
      role: "END_USER",
    });
    renderApp("/");

    expect(await screen.findByRole("heading", { name: "Hello, Ana Sousa" })).toBeInTheDocument();
    expect(getProfile).toHaveBeenCalledWith(session.userId, "token-for-ana");
  });

  it.each([
    ["END_USER", true],
    ["OPERATOR", false],
  ])("offers buying a pass to a tourist only (%s)", async (role, offered) => {
    saveSession(session);
    vi.mocked(getProfile).mockResolvedValue({
      id: session.userId,
      name: "Ana Sousa",
      email: "ana.sousa@example.com",
      role,
    });
    renderApp("/");

    await screen.findByRole("heading", { name: "Hello, Ana Sousa" });
    const link = screen.queryByRole("link", { name: "Buy a pass" });
    if (offered) {
      expect(link).toHaveAttribute("href", "/passes/buy");
    } else {
      expect(link).not.toBeInTheDocument();
    }
  });

  it("forgets an expired session and asks to log in again", async () => {
    saveSession(session);
    vi.mocked(getProfile).mockRejectedValue(new ApiError(401, {}));
    renderApp("/");

    expect(await screen.findByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(loadSession()).toBeNull();
  });

  it("logs out", async () => {
    saveSession(session);
    vi.mocked(getProfile).mockResolvedValue({
      id: session.userId,
      name: "Ana Sousa",
      email: "ana.sousa@example.com",
      role: "END_USER",
    });
    renderApp("/");

    await userEvent.setup().click(await screen.findByRole("button", { name: "Log out" }));

    expect(screen.getByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(loadSession()).toBeNull();
  });

  it("offers account management to an admin", async () => {
    saveSession(session);
    vi.mocked(getProfile).mockResolvedValue({
      id: session.userId,
      name: "Marta Reis",
      email: "marta.reis@example.com",
      role: "ADMIN",
    });
    renderApp("/");

    expect(await screen.findByRole("link", { name: "Manage accounts" })).toHaveAttribute(
      "href",
      "/admin/users",
    );
  });

  it("does not offer account management to a tourist", async () => {
    saveSession(session);
    vi.mocked(getProfile).mockResolvedValue({
      id: session.userId,
      name: "Ana Sousa",
      email: "ana.sousa@example.com",
      role: "END_USER",
    });
    renderApp("/");

    await screen.findByRole("heading", { name: "Hello, Ana Sousa" });
    expect(screen.queryByRole("link", { name: "Manage accounts" })).not.toBeInTheDocument();
  });

  it("offers fleet management to an operator", async () => {
    saveSession(session);
    vi.mocked(getProfile).mockResolvedValue({
      id: session.userId,
      name: "Rui Costa",
      email: "rui.costa@example.com",
      role: "OPERATOR",
    });
    renderApp("/");

    expect(await screen.findByRole("link", { name: "Manage fleet" })).toHaveAttribute(
      "href",
      "/operator/fleet",
    );
    expect(screen.queryByRole("link", { name: "Manage accounts" })).not.toBeInTheDocument();
  });

  it("does not offer fleet management to a tourist", async () => {
    saveSession(session);
    vi.mocked(getProfile).mockResolvedValue({
      id: session.userId,
      name: "Ana Sousa",
      email: "ana.sousa@example.com",
      role: "END_USER",
    });
    renderApp("/");

    await screen.findByRole("heading", { name: "Hello, Ana Sousa" });
    expect(screen.queryByRole("link", { name: "Manage fleet" })).not.toBeInTheDocument();
  });

  it.each(["END_USER", "OPERATOR", "CLIENT_MANAGER", "ADMIN"])(
    "offers the vehicle search to a %s",
    async (role) => {
      saveSession(session);
      vi.mocked(getProfile).mockResolvedValue({
        id: session.userId,
        name: "Ana Sousa",
        email: "ana.sousa@example.com",
        role,
      });
      renderApp("/");

      await screen.findByRole("heading", { name: "Hello, Ana Sousa" });
      expect(screen.getByRole("link", { name: "Find a vehicle" })).toHaveAttribute(
        "href",
        "/vehicles/search",
      );
    },
  );
});
