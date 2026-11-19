import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { changeRole, createAccount, listAccounts, setActive, type Account } from "../api/admin";
import { getProfile } from "../api/auth";
import { ApiError } from "../api/client";
import { saveSession } from "../auth/session";
import { renderApp } from "../test/renderApp";

vi.mock("../api/auth");
vi.mock("../api/admin");

const TOKEN = "token-for-marta";

const marta: Account = {
  id: "9f8e7d6c-5b4a-4321-8fed-cba987654321",
  name: "Marta Reis",
  email: "marta.reis@example.com",
  role: "ADMIN",
  fieldTechnician: false,
  active: true,
};

const rui: Account = {
  id: "4b1d6f2e-8a3c-4e5b-9d7f-1a2b3c4d5e6f",
  name: "Rui Costa",
  email: "rui.costa@example.com",
  role: "OPERATOR",
  fieldTechnician: false,
  active: true,
};

function openAs(role: Account["role"], accounts: Account[] = [marta, rui]) {
  saveSession({ userId: marta.id, accessToken: TOKEN });
  vi.mocked(getProfile).mockResolvedValue({
    id: marta.id,
    name: marta.name,
    email: marta.email,
    role,
  });
  vi.mocked(listAccounts).mockResolvedValue(accounts);
  renderApp("/admin/users");
}

async function rowOf(name: string) {
  return within(await screen.findByRole("row", { name: new RegExp(name) }));
}

async function newAccountForm() {
  return within(await screen.findByRole("form", { name: "New account" }));
}

describe("account management page", () => {
  it("sends a visitor without a session to the login", () => {
    renderApp("/admin/users");

    expect(screen.getByRole("heading", { name: "Log in" })).toBeInTheDocument();
  });

  it("tells someone who is not an admin that they have no access", async () => {
    openAs("OPERATOR");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You do not have access to this page.",
    );
    expect(listAccounts).not.toHaveBeenCalled();
  });

  it("lists every account with the admin's token", async () => {
    openAs("ADMIN");

    expect(await screen.findByRole("row", { name: /Marta Reis/ })).toBeInTheDocument();
    expect(screen.getByRole("row", { name: /Rui Costa/ })).toBeInTheDocument();
    expect(listAccounts).toHaveBeenCalledWith(TOKEN);
  });

  it("creates an operator who is a field technician and shows them in the list", async () => {
    vi.mocked(createAccount).mockResolvedValue({ ...rui, fieldTechnician: true });
    openAs("ADMIN", [marta]);
    const user = userEvent.setup();
    const form = await newAccountForm();

    await user.type(form.getByLabelText("Name"), "Rui Costa");
    await user.type(form.getByLabelText("Email"), "rui.costa@example.com");
    await user.type(form.getByLabelText("Password"), "Passw0rd!");
    await user.selectOptions(form.getByLabelText("Role"), "Operator");
    await user.click(form.getByLabelText("Field technician"));
    await user.click(form.getByRole("button", { name: "Create account" }));

    expect(createAccount).toHaveBeenCalledWith(
      {
        name: "Rui Costa",
        email: "rui.costa@example.com",
        password: "Passw0rd!",
        role: "OPERATOR",
        fieldTechnician: true,
      },
      TOKEN,
    );
    expect(await screen.findByRole("row", { name: /Rui Costa/ })).toBeInTheDocument();
  });

  it("only lets an operator be a field technician (BR20)", async () => {
    openAs("ADMIN");
    const form = await newAccountForm();

    await userEvent.setup().selectOptions(form.getByLabelText("Role"), "Client manager");

    expect(form.getByLabelText("Field technician")).toBeDisabled();
  });

  it("shows a field the server refused next to it", async () => {
    vi.mocked(createAccount).mockRejectedValue(
      new ApiError(400, { email: "must be a well-formed email address" }),
    );
    openAs("ADMIN");
    const user = userEvent.setup();
    const form = await newAccountForm();

    await user.type(form.getByLabelText("Name"), "Rui Costa");
    await user.type(form.getByLabelText("Email"), "rui.costa");
    await user.click(form.getByRole("button", { name: "Create account" }));

    expect(await form.findByText("Enter a valid email.")).toBeInTheDocument();
    expect(form.getByLabelText("Email")).toHaveAccessibleDescription("Enter a valid email.");
  });

  it("says when the email is already in use", async () => {
    vi.mocked(createAccount).mockRejectedValue(new ApiError(409, {}));
    openAs("ADMIN");
    const form = await newAccountForm();

    await userEvent.setup().click(form.getByRole("button", { name: "Create account" }));

    expect(await form.findByText("This email is already in use.")).toBeInTheDocument();
  });

  it("changes the role of an account", async () => {
    vi.mocked(changeRole).mockResolvedValue({ ...rui, role: "CLIENT_MANAGER" });
    openAs("ADMIN");
    const user = userEvent.setup();
    const row = await rowOf("Rui Costa");

    await user.selectOptions(row.getByLabelText("Role for Rui Costa"), "Client manager");
    await user.click(row.getByRole("button", { name: "Save" }));

    expect(changeRole).toHaveBeenCalledWith(rui.id, "CLIENT_MANAGER", false, TOKEN);
    expect(row.getByLabelText("Role for Rui Costa")).toHaveValue("CLIENT_MANAGER");
  });

  it("deactivates an account and offers to activate it again", async () => {
    vi.mocked(setActive).mockResolvedValue({ ...rui, active: false });
    openAs("ADMIN");
    const row = await rowOf("Rui Costa");

    await userEvent.setup().click(row.getByRole("button", { name: "Deactivate" }));

    expect(setActive).toHaveBeenCalledWith(rui.id, false, TOKEN);
    expect(await row.findByText("Inactive")).toBeInTheDocument();
    expect(row.getByRole("button", { name: "Activate" })).toBeInTheDocument();
  });

  it("offers no changes on admin accounts, which are managed in the database", async () => {
    openAs("ADMIN");
    const row = await rowOf("Marta Reis");

    expect(row.queryByRole("button")).not.toBeInTheDocument();
    expect(row.queryByRole("combobox")).not.toBeInTheDocument();
  });

  it("shows an error on the row when a change fails", async () => {
    vi.mocked(setActive).mockRejectedValue(new ApiError(500, {}));
    openAs("ADMIN");
    const row = await rowOf("Rui Costa");

    await userEvent.setup().click(row.getByRole("button", { name: "Deactivate" }));

    expect(await row.findByRole("alert")).toHaveTextContent(
      "Something went wrong. Please try again.",
    );
    expect(row.getByText("Active")).toBeInTheDocument();
  });
});