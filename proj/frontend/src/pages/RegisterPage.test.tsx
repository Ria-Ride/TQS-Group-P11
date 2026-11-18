import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { getProfile, registerGuest } from "../api/auth";
import { ApiError } from "../api/client";
import { loadSession } from "../auth/session";
import { renderApp } from "../test/renderApp";

vi.mock("../api/auth");

const grant = {
  userId: "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  accessToken: "token-for-ana",
  tokenType: "Bearer",
};
const profile = {
  id: grant.userId,
  name: "Ana Sousa",
  email: "ana.sousa@example.com",
  role: "END_USER",
};

async function register(name: string, email: string, password: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Name"), name);
  await user.type(screen.getByLabelText("Email"), email);
  await user.type(screen.getByLabelText("Password"), password);
  await user.click(screen.getByRole("button", { name: "Create account" }));
}

describe("registration page", () => {
  it("creates the account, keeps the session and opens the home page", async () => {
    vi.mocked(registerGuest).mockResolvedValue(grant);
    vi.mocked(getProfile).mockResolvedValue(profile);
    renderApp("/register");

    await register("Ana Sousa", "ana.sousa@example.com", "Passw0rd!");

    expect(await screen.findByRole("heading", { name: "Hello, Ana Sousa" })).toBeInTheDocument();
    expect(registerGuest).toHaveBeenCalledWith({
      name: "Ana Sousa",
      email: "ana.sousa@example.com",
      password: "Passw0rd!",
    });
    expect(loadSession()).toEqual({ userId: grant.userId, accessToken: "token-for-ana" });
  });

  it("shows a validation error next to the field it is about", async () => {
    vi.mocked(registerGuest).mockRejectedValue(
      new ApiError(400, { email: "must match \"^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$\"" }),
    );
    renderApp("/register");

    await register("Ana Sousa", "ana.sousa@localhost", "Passw0rd!");

    const message = "Enter a valid email address, like ana@example.com.";
    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toHaveAccessibleDescription(message);
    expect(screen.getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByLabelText("Name")).not.toHaveAccessibleDescription();
    expect(loadSession()).toBeNull();
  });

  it("explains when the email already has an account and links to the login", async () => {
    vi.mocked(registerGuest).mockRejectedValue(
      new ApiError(409, { email: "is already in use" }),
    );
    renderApp("/register");

    await register("Ana Sousa", "ana.sousa@example.com", "Passw0rd!");

    const message = "This email already has an account. Log in instead.";
    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toHaveAccessibleDescription(message);
    expect(screen.getByRole("link", { name: "Log in" })).toHaveAttribute("href", "/login");
  });

  it("shows a general error when the server cannot be reached", async () => {
    vi.mocked(registerGuest).mockRejectedValue(new TypeError("Failed to fetch"));
    renderApp("/register");

    await register("Ana Sousa", "ana.sousa@example.com", "Passw0rd!");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Something went wrong. Please try again.",
    );
  });
});