import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { getProfile } from "../api/auth";
import { ApiError } from "../api/client";
import { listVehicles, type Vehicle } from "../api/fleet";
import {
  assignTicket,
  listOpenTickets,
  listTechnicians,
  openTicket,
  type Technician,
  type Ticket,
} from "../api/maintenance";
import { loadSession, saveSession } from "../auth/session";
import { renderApp } from "../test/renderApp";

vi.mock("../api/auth");
vi.mock("../api/fleet");
vi.mock("../api/maintenance");

const TOKEN = "token-for-rui";
const USER_ID = "4b1d6f2e-8a3c-4e5b-9d7f-1a2b3c4d5e6f";
const HUB_ID = "2b3c4d5e-0000-4000-8000-000000000103";

const bike: Vehicle = {
  id: "id-bike-0009",
  code: "bike-0009",
  type: "E_BIKE",
  state: "MAINTENANCE",
  hubId: HUB_ID,
};
const scooter: Vehicle = {
  id: "id-scooter-0007",
  code: "scooter-0007",
  type: "E_SCOOTER",
  state: "AVAILABLE",
  hubId: HUB_ID,
};
const retired: Vehicle = {
  id: "id-bike-0001",
  code: "bike-0001",
  type: "E_BIKE",
  state: "RETIRED",
  hubId: null,
};

const tiago: Technician = { id: "tech-tiago", name: "Tiago Lopes" };
const ana: Technician = { id: "tech-ana", name: "Ana Matos" };

/** From a fault reading: no operator, no description, not assigned yet. */
const brakes: Ticket = {
  id: "ticket-brakes",
  vehicleId: bike.id,
  faultCode: "BRAKE_WEAR",
  description: null,
  state: "OPEN",
  openedAt: "2026-11-10T10:00:00Z",
  slaDeadline: "2026-11-11T10:00:00Z",
  openedBy: null,
  technicianId: null,
  automatic: true,
  closedAt: null,
  workDone: null,
  timeToRepairMinutes: null,
  breached: false,
};

/** Opened by an operator and already assigned; its deadline is the nearest. */
const basket: Ticket = {
  id: "ticket-basket",
  vehicleId: scooter.id,
  faultCode: "BROKEN_BASKET",
  description: "Front basket cracked",
  state: "ASSIGNED",
  openedAt: "2026-11-10T08:00:00Z",
  slaDeadline: "2026-11-11T08:00:00Z",
  openedBy: USER_ID,
  technicianId: tiago.id,
  automatic: false,
  closedAt: null,
  workDone: null,
  timeToRepairMinutes: null,
  breached: false,
};

function openAs(
  role: string,
  tickets: Ticket[] = [basket, brakes],
  technicians: Technician[] = [ana, tiago],
) {
  saveSession({ userId: USER_ID, accessToken: TOKEN });
  vi.mocked(getProfile).mockResolvedValue({
    id: USER_ID,
    name: "Rui Costa",
    email: "rui.costa@example.com",
    role,
  });
  vi.mocked(listOpenTickets).mockResolvedValue(tickets);
  vi.mocked(listVehicles).mockResolvedValue([bike, scooter, retired]);
  vi.mocked(listTechnicians).mockResolvedValue(technicians);
  renderApp("/operator/tickets");
}

/** What the server lists after a change; the page reloads the tickets from it. */
function afterTheChange(tickets: Ticket[]) {
  vi.mocked(listOpenTickets).mockResolvedValue(tickets);
}

async function ticketsTable() {
  return within(await screen.findByRole("table", { name: "Open tickets" }));
}

async function ticketRow(fault: string) {
  const table = await ticketsTable();
  return within(await table.findByRole("row", { name: new RegExp(fault) }));
}

async function newTicketForm() {
  return within(await screen.findByRole("form", { name: "New ticket" }));
}

describe("tickets page", () => {
  // ------------------------------------------------------------- access

  it("sends a visitor without a session to the login", () => {
    renderApp("/operator/tickets");

    expect(screen.getByRole("heading", { name: "Log in" })).toBeInTheDocument();
  });

  it("tells a tourist they have no access and loads nothing", async () => {
    openAs("END_USER");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You do not have access to this page.",
    );
    expect(listOpenTickets).not.toHaveBeenCalled();
    expect(listTechnicians).not.toHaveBeenCalled();
  });

  it("forgets an expired session and asks to log in again", async () => {
    openAs("OPERATOR");
    vi.mocked(getProfile).mockRejectedValue(new ApiError(401, {}));
    renderApp("/operator/tickets");

    expect(await screen.findByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(loadSession()).toBeNull();
  });

  it("tells the user they have no access when the API refuses", async () => {
    openAs("OPERATOR");
    vi.mocked(listOpenTickets).mockRejectedValue(new ApiError(403, {}));
    renderApp("/operator/tickets");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You do not have access to this page.",
    );
  });

  // ------------------------------------------------------------- the list

  it("lists the open tickets in the order of the server, nearest deadline first", async () => {
    openAs("OPERATOR");

    const rows = (await ticketsTable()).getAllByRole("row");

    // The first row is the header.
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent("BROKEN_BASKET");
    expect(rows[2]).toHaveTextContent("BRAKE_WEAR");
  });

  it("shows the vehicle, fault, origin, state, deadline and technician of each ticket", async () => {
    openAs("OPERATOR");

    const assigned = await ticketRow("BROKEN_BASKET");
    expect(assigned.getByRole("cell", { name: "scooter-0007" })).toBeInTheDocument();
    expect(assigned.getByText("Front basket cracked")).toBeInTheDocument();
    expect(assigned.getByText("Operator")).toBeInTheDocument();
    expect(assigned.getByText("Assigned")).toBeInTheDocument();
    // The name is also a choice of the technician select, so look for its cell.
    expect(assigned.getByRole("cell", { name: "Tiago Lopes" })).toBeInTheDocument();
    expect(
      assigned.getByText(new Date(basket.slaDeadline).toLocaleString()),
    ).toBeInTheDocument();

    const open = await ticketRow("BRAKE_WEAR");
    expect(open.getByRole("cell", { name: "bike-0009" })).toBeInTheDocument();
    expect(open.getByText("Telemetry")).toBeInTheDocument();
    expect(open.getByText("Open")).toBeInTheDocument();
    expect(open.getByText("Not assigned")).toBeInTheDocument();
  });

  it("says when there are no open tickets", async () => {
    openAs("OPERATOR", []);

    expect(await screen.findByText("No open tickets.")).toBeInTheDocument();
  });

  // ------------------------------------------------------------- a manual ticket

  it("opens a ticket for a fault the simulator cannot detect and shows it", async () => {
    const user = userEvent.setup();
    openAs("OPERATOR", [brakes]);
    const created: Ticket = {
      ...basket,
      id: "ticket-tyre",
      faultCode: "FLAT_TYRE",
      description: "Rear tyre flat",
      state: "OPEN",
      technicianId: null,
    };
    vi.mocked(openTicket).mockResolvedValue(created);
    const form = await newTicketForm();

    await user.selectOptions(form.getByLabelText("Vehicle"), "scooter-0007");
    await user.type(form.getByLabelText("Fault code"), "FLAT_TYRE");
    await user.type(form.getByLabelText("Description"), "Rear tyre flat");
    afterTheChange([brakes, created]);
    await user.click(form.getByRole("button", { name: "Open ticket" }));

    expect(openTicket).toHaveBeenCalledWith(
      { vehicleId: scooter.id, faultCode: "FLAT_TYRE", description: "Rear tyre flat" },
      TOKEN,
    );
    expect(await ticketRow("FLAT_TYRE")).toBeTruthy();
  });

  it("does not offer retired vehicles for a new ticket", async () => {
    openAs("OPERATOR");
    const form = await newTicketForm();

    const choices = within(form.getByLabelText("Vehicle"))
      .getAllByRole("option")
      .map((option) => option.textContent);

    expect(choices).toEqual(["bike-0009", "scooter-0007"]);
  });

  it("explains a fault that already has an open ticket (BR20)", async () => {
    const user = userEvent.setup();
    openAs("OPERATOR");
    vi.mocked(openTicket).mockRejectedValue(
      new ApiError(409, { faultCode: "is already covered by an open ticket" }),
    );
    const form = await newTicketForm();

    await user.type(form.getByLabelText("Fault code"), "BRAKE_WEAR");
    await user.type(form.getByLabelText("Description"), "Brakes squeak");
    await user.click(form.getByRole("button", { name: "Open ticket" }));

    expect(await form.findByText("This fault already has an open ticket.")).toBeInTheDocument();
  });

  it("explains invalid fields next to each one", async () => {
    const user = userEvent.setup();
    openAs("OPERATOR");
    vi.mocked(openTicket).mockRejectedValue(
      new ApiError(400, { faultCode: "must be UPPER_SNAKE_CASE", description: "must not be blank" }),
    );
    const form = await newTicketForm();

    await user.type(form.getByLabelText("Fault code"), "brake wear");
    await user.click(form.getByRole("button", { name: "Open ticket" }));

    expect(
      await form.findByText("Use capital letters, digits and _, starting with a letter."),
    ).toBeInTheDocument();
    expect(form.getByText("Describe what you saw.")).toBeInTheDocument();
  });

  // ------------------------------------------------------------- assignment (BR20)

  it("assigns a ticket to a field technician and shows the new assignee", async () => {
    const user = userEvent.setup();
    openAs("OPERATOR", [brakes]);
    vi.mocked(assignTicket).mockResolvedValue({
      ...brakes,
      state: "ASSIGNED",
      technicianId: ana.id,
    });
    const row = await ticketRow("BRAKE_WEAR");

    await user.selectOptions(row.getByRole("combobox", { name: "Technician" }), "Ana Matos");
    afterTheChange([{ ...brakes, state: "ASSIGNED", technicianId: ana.id }]);
    await user.click(row.getByRole("button", { name: "Assign" }));

    expect(assignTicket).toHaveBeenCalledWith(brakes.id, ana.id, TOKEN);
    const updated = await ticketRow("BRAKE_WEAR");
    expect(await updated.findByText("Assigned")).toBeInTheDocument();
  });

  it("offers the field technicians by the order of the server", async () => {
    openAs("OPERATOR", [brakes]);
    const row = await ticketRow("BRAKE_WEAR");

    const choices = within(row.getByRole("combobox", { name: "Technician" }))
      .getAllByRole("option")
      .map((option) => option.textContent);

    expect(choices).toEqual(["Choose a technician", "Ana Matos", "Tiago Lopes"]);
  });

  it("does not assign before a technician is chosen", async () => {
    openAs("OPERATOR", [brakes]);
    const row = await ticketRow("BRAKE_WEAR");

    expect(row.getByRole("button", { name: "Assign" })).toBeDisabled();
  });

  it("explains an account that cannot take tickets", async () => {
    const user = userEvent.setup();
    openAs("OPERATOR", [brakes]);
    vi.mocked(assignTicket).mockRejectedValue(
      new ApiError(400, { technicianId: "is not an active field technician" }),
    );
    const row = await ticketRow("BRAKE_WEAR");

    await user.selectOptions(row.getByRole("combobox", { name: "Technician" }), "Ana Matos");
    await user.click(row.getByRole("button", { name: "Assign" }));

    expect(await row.findByRole("alert")).toHaveTextContent(
      "Choose an active field technician.",
    );
  });

  it("explains that a ticket whose work has started keeps its technician", async () => {
    const user = userEvent.setup();
    openAs("OPERATOR", [brakes]);
    vi.mocked(assignTicket).mockRejectedValue(new ApiError(409, { state: "cannot be assigned" }));
    const row = await ticketRow("BRAKE_WEAR");

    await user.selectOptions(row.getByRole("combobox", { name: "Technician" }), "Ana Matos");
    await user.click(row.getByRole("button", { name: "Assign" }));

    expect(await row.findByRole("alert")).toHaveTextContent(
      "Work on this ticket has started; it keeps its technician.",
    );
  });

  it("says when there is no field technician to assign", async () => {
    openAs("OPERATOR", [brakes], []);

    expect(await screen.findByText("No field technicians yet.")).toBeInTheDocument();
    const row = await ticketRow("BRAKE_WEAR");
    expect(row.getByRole("combobox", { name: "Technician" })).toBeDisabled();
  });
});
