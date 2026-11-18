import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { getProfile, login } from "../api/auth";
import { ApiError } from "../api/client";
import { loadSession } from "../auth/session";
import { renderApp } from "../test/renderApp";

vi.mock("../api/auth");

const grant = {
  userId: "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  accessToken: "token-for-ana",
  tokenType: "Bearer",
};

async function logIn(email: string, password: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Email"), email);
  await user.type(screen.getByLabelText("Password"), password);
  await user.click(screen.getByRole("button", { name: "Log in" }));
}

describe("login page", () => {
  it("logs in, keeps the session and opens the home page", async () => {
    vi.mocked(login).mockResolvedValue(grant);
    vi.mocked(getProfile).mockResolvedValue({
      id: grant.userId,
      name: "Ana Sousa",
      email: "ana.sousa@example.com",
      role: "END_USER",
    });
    renderApp("/login");

    await logIn("ana.sousa@example.com", "Passw0rd!");

    expect(await screen.findByRole("heading", { name: "Hello, Ana Sousa" })).toBeInTheDocument();
    expect(login).toHaveBeenCalledWith({ email: "ana.sousa@example.com", password: "Passw0rd!" });
    expect(loadSession()).toEqual({ userId: grant.userId, accessToken: "token-for-ana" });
  });

  it("says the credentials are wrong without saying which one", async () => {
    vi.mocked(login).mockRejectedValue(new ApiError(401, {}));
    renderApp("/login");

    await logIn("ana.sousa@example.com", "WrongPass1!");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The email or password is incorrect.",
    );
    expect(loadSession()).toBeNull();
  });

  it("shows a missing field next to it", async () => {
    vi.mocked(login).mockRejectedValue(new ApiError(400, { password: "must not be blank" }));
    renderApp("/login");
    const user = userEvent.setup();

    await user.type(screen.getByLabelText("Email"), "ana.sousa@example.com");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    expect(await screen.findByText("Enter your password.")).toBeInTheDocument();
    expect(screen.getByLabelText("Password")).toHaveAccessibleDescription("Enter your password.");
  });

  it("links to the guest registration", async () => {
    renderApp("/login");

    await userEvent.setup().click(screen.getByRole("link", { name: "Create a guest account" }));

    expect(
      screen.getByRole("heading", { name: "Create your guest account" }),
    ).toBeInTheDocument();
  });
});