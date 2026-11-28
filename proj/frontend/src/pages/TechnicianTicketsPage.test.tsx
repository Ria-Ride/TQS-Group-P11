import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { getProfile } from "../api/auth";
import { ApiError } from "../api/client";
import {
  closeTicket,
  listMyTickets,
  startTicket,
  type TechnicianTicket,
  type Ticket,
} from "../api/maintenance";
import { loadSession, saveSession } from "../auth/session";
import { renderApp } from "../test/renderApp";

vi.mock("../api/auth");
vi.mock("../api/maintenance");

const TOKEN = "token-for-tiago";
const USER_ID = "5d2e7f3a-9b4c-4f6d-8e0a-2b3c4d5e6f70";

/** Late: its deadline has passed. At a hub, so the technician knows where to go. */
const brakes: TechnicianTicket = {
  id: "ticket-brakes",
  vehicleId: "id-bike-0009",
  vehicleCode: "bike-0009",
  faultCode: "BRAKE_WEAR",
  description: null,
  state: "ASSIGNED",
  openedAt: "2026-11-10T09:00:00Z",
  slaDeadline: "2026-11-11T09:00:00Z",
  hubCode: "hub-03",
  hubName: "Canal Central",
  latitude: 40.6405,
  longitude: -8.6538,
  breached: true,
};

/** Work started; the vehicle is at no hub, so only its last position is known. */
const tyre: TechnicianTicket = {
  id: "ticket-tyre",
  vehicleId: "id-scooter-0007",
  vehicleCode: "scooter-0007",
  faultCode: "FLAT_TIRE",
  description: "Rear tyre flat",
  state: "IN_PROGRESS",
  openedAt: "2026-11-10T11:00:00Z",
  slaDeadline: "2026-11-11T11:00:00Z",
  hubCode: null,
  hubName: null,
  latitude: 40.6421,
  longitude: -8.6524,
  breached: false,
};

/** What the server answers to a start or a close; the page only reloads the list after it. */
const answer = { id: "any" } as Ticket;

function openAs(
  role: string,
  fieldTechnician: boolean,
  tickets: TechnicianTicket[] = [brakes, tyre],
) {
  saveSession({ userId: USER_ID, accessToken: TOKEN });
  vi.mocked(getProfile).mockResolvedValue({
    id: USER_ID,
    name: "Tiago Lopes",
    email: "tiago.lopes@example.com",
    role,
    fieldTechnician,
  });
  vi.mocked(listMyTickets).mockResolvedValue(tickets);
  renderApp("/technician/tickets");
}

function openAsTechnician(tickets?: TechnicianTicket[]) {
  openAs("OPERATOR", true, tickets);
}

/** What the server lists after a change; the page reloads the tickets from it. */
function afterTheChange(tickets: TechnicianTicket[]) {
  vi.mocked(listMyTickets).mockResolvedValue(tickets);
}

async function myTicketsTable() {
  return within(await screen.findByRole("table", { name: "My tickets" }));
}

async function ticketRow(fault: string) {
  const table = await myTicketsTable();
  return within(await table.findByRole("row", { name: new RegExp(fault) }));
}

describe("technician tickets page", () => {
  // ------------------------------------------------------------- access

  it("sends a visitor without a session to the login", () => {
    renderApp("/technician/tickets");

    expect(screen.getByRole("heading", { name: "Log in" })).toBeInTheDocument();
  });

  it.each([
    ["OPERATOR", false],
    ["ADMIN", false],
    ["END_USER", false],
  ])("tells a %s who is not a field technician they have no access", async (role, technician) => {
    openAs(role, technician);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You do not have access to this page.",
    );
    expect(listMyTickets).not.toHaveBeenCalled();
  });

  it("forgets an expired session and asks to log in again", async () => {
    openAsTechnician();
    vi.mocked(getProfile).mockRejectedValue(new ApiError(401, {}));
    renderApp("/technician/tickets");

    expect(await screen.findByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(loadSession()).toBeNull();
  });

  // ------------------------------------------------------------- the list (BR19)

  it("lists the tickets in the order of the server, nearest deadline first", async () => {
    openAsTechnician();

    const rows = (await myTicketsTable()).getAllByRole("row");

    // The first row is the header.
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent("BRAKE_WEAR");
    expect(rows[2]).toHaveTextContent("FLAT_TIRE");
  });

  it("shows the vehicle, the fault, where the vehicle is, the deadline and the state", async () => {
    openAsTechnician();

    const atHub = await ticketRow("BRAKE_WEAR");
    expect(atHub.getByRole("cell", { name: "bike-0009" })).toBeInTheDocument();
    expect(atHub.getByText("hub-03 · Canal Central")).toBeInTheDocument();
    expect(atHub.getByText(new Date(brakes.slaDeadline).toLocaleString())).toBeInTheDocument();
    expect(atHub.getByText("Assigned")).toBeInTheDocument();

    const riding = await ticketRow("FLAT_TIRE");
    expect(riding.getByText("Rear tyre flat")).toBeInTheDocument();
    expect(riding.getByText("40.6421, -8.6524")).toBeInTheDocument();
    expect(riding.getByText("In progress")).toBeInTheDocument();
  });

  it("marks a ticket past its deadline as late, and only that one", async () => {
    openAsTechnician();

    expect((await ticketRow("BRAKE_WEAR")).getByText("Late")).toBeInTheDocument();
    expect((await ticketRow("FLAT_TIRE")).queryByText("Late")).not.toBeInTheDocument();
  });

  it("says when no ticket is assigned to the technician", async () => {
    openAsTechnician([]);

    expect(await screen.findByText("No tickets assigned to you.")).toBeInTheDocument();
  });

  // ------------------------------------------------------------- starting the work

  it("starts the work on an assigned ticket and shows it in progress", async () => {
    const user = userEvent.setup();
    openAsTechnician([brakes]);
    vi.mocked(startTicket).mockResolvedValue(answer);
    const row = await ticketRow("BRAKE_WEAR");

    afterTheChange([{ ...brakes, state: "IN_PROGRESS" }]);
    await user.click(row.getByRole("button", { name: "Start" }));

    expect(startTicket).toHaveBeenCalledWith(brakes.id, TOKEN);
    expect(await (await ticketRow("BRAKE_WEAR")).findByText("In progress")).toBeInTheDocument();
  });

  it("offers to close only a ticket whose work has started", async () => {
    openAsTechnician();

    const assigned = await ticketRow("BRAKE_WEAR");
    expect(assigned.getByRole("button", { name: "Start" })).toBeInTheDocument();
    expect(assigned.queryByRole("button", { name: "Close" })).not.toBeInTheDocument();

    const started = await ticketRow("FLAT_TIRE");
    expect(started.getByRole("button", { name: "Close" })).toBeInTheDocument();
    expect(started.queryByRole("button", { name: "Start" })).not.toBeInTheDocument();
  });

  it("explains a ticket that is assigned to someone else", async () => {
    const user = userEvent.setup();
    openAsTechnician([brakes]);
    vi.mocked(startTicket).mockRejectedValue(new ApiError(403, {}));
    const row = await ticketRow("BRAKE_WEAR");

    await user.click(row.getByRole("button", { name: "Start" }));

    expect(await row.findByRole("alert")).toHaveTextContent(
      "This ticket is assigned to another technician.",
    );
  });

  // ------------------------------------------------------------- closing (BR20)

  it("closes a ticket with the work done, and it leaves the list", async () => {
    const user = userEvent.setup();
    openAsTechnician([brakes, tyre]);
    vi.mocked(closeTicket).mockResolvedValue(answer);
    const row = await ticketRow("FLAT_TIRE");

    await user.type(row.getByLabelText("Work done"), "Replaced the inner tube");
    afterTheChange([brakes]);
    await user.click(row.getByRole("button", { name: "Close" }));

    expect(closeTicket).toHaveBeenCalledWith(tyre.id, "Replaced the inner tube", TOKEN);
    const table = await myTicketsTable();
    await vi.waitFor(() =>
      expect(table.queryByRole("row", { name: /FLAT_TIRE/ })).not.toBeInTheDocument(),
    );
  });

  it("does not close before the work done is written", async () => {
    const user = userEvent.setup();
    openAsTechnician([tyre]);
    const row = await ticketRow("FLAT_TIRE");

    expect(row.getByRole("button", { name: "Close" })).toBeDisabled();
    await user.type(row.getByLabelText("Work done"), "   ");
    expect(row.getByRole("button", { name: "Close" })).toBeDisabled();
  });

  it("explains a work done the server refuses", async () => {
    const user = userEvent.setup();
    openAsTechnician([tyre]);
    vi.mocked(closeTicket).mockRejectedValue(
      new ApiError(400, { workDone: "must not be blank" }),
    );
    const row = await ticketRow("FLAT_TIRE");

    await user.type(row.getByLabelText("Work done"), "x");
    await user.click(row.getByRole("button", { name: "Close" }));

    expect(await row.findByRole("alert")).toHaveTextContent("Describe the work done.");
  });

  it("explains a ticket whose work has not started", async () => {
    const user = userEvent.setup();
    openAsTechnician([tyre]);
    vi.mocked(closeTicket).mockRejectedValue(new ApiError(409, { state: "not started" }));
    const row = await ticketRow("FLAT_TIRE");

    await user.type(row.getByLabelText("Work done"), "Replaced the inner tube");
    await user.click(row.getByRole("button", { name: "Close" }));

    expect(await row.findByRole("alert")).toHaveTextContent(
      "Start the work before closing the ticket.",
    );
  });
});
