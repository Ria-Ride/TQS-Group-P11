import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { getProfile } from "../api/auth";
import { ApiError } from "../api/client";
import {
  changeCapacity,
  listHubs,
  listVehicles,
  registerHub,
  registerVehicle,
  transition,
  vehicleHistory,
  type Hub,
  type Vehicle,
} from "../api/fleet";
import { loadSession, saveSession } from "../auth/session";
import { renderApp } from "../test/renderApp";

vi.mock("../api/auth");
vi.mock("../api/fleet");

const TOKEN = "token-for-rui";
const USER_ID = "4b1d6f2e-8a3c-4e5b-9d7f-1a2b3c4d5e6f";

const hub03: Hub = {
  id: "2b3c4d5e-0000-4000-8000-000000000103",
  code: "hub-03",
  name: "Canal Central",
  latitude: 40.6405,
  longitude: -8.6538,
  capacity: 10,
  vehiclesHeld: 1,
};

const hub05: Hub = {
  id: "2b3c4d5e-0000-4000-8000-000000000105",
  code: "hub-05",
  name: "Hotel Ria Azul",
  latitude: 40.6421,
  longitude: -8.6524,
  capacity: 10,
  vehiclesHeld: 0,
};

const bike: Vehicle = {
  id: "3c4d5e6f-0000-4000-8000-000000000009",
  code: "bike-0009",
  type: "E_BIKE",
  state: "DEACTIVATED",
  hubId: hub03.id,
};

function vehicle(code: string, state: Vehicle["state"], hubId: string | null): Vehicle {
  return { ...bike, id: `id-${code}`, code, state, hubId };
}

function openAs(role: string, hubs: Hub[] = [hub03, hub05], vehicles: Vehicle[] = [bike]) {
  saveSession({ userId: USER_ID, accessToken: TOKEN });
  vi.mocked(getProfile).mockResolvedValue({
    id: USER_ID,
    name: "Rui Costa",
    email: "rui.costa@example.com",
    role,
  });
  vi.mocked(listHubs).mockResolvedValue(hubs);
  vi.mocked(listVehicles).mockResolvedValue(vehicles);
  renderApp("/operator/fleet");
}

/** What the lists hold after a change; the page reloads them from the server. */
function afterTheChange(lists: { hubs?: Hub[]; vehicles?: Vehicle[] }) {
  if (lists.hubs) vi.mocked(listHubs).mockResolvedValue(lists.hubs);
  if (lists.vehicles) vi.mocked(listVehicles).mockResolvedValue(lists.vehicles);
}

/** A row of one table: the vehicles table also shows hub codes, so search the right one. */
async function rowIn(table: "Hubs" | "Vehicles", code: string) {
  const rows = within(await screen.findByRole("table", { name: table }));
  return within(await rows.findByRole("row", { name: new RegExp(code) }));
}

const hubRow = (code: string) => rowIn("Hubs", code);
const vehicleRow = (code: string) => rowIn("Vehicles", code);

async function form(name: string) {
  return within(await screen.findByRole("form", { name }));
}

describe("fleet page", () => {
  it("sends a visitor without a session to the login", () => {
    renderApp("/operator/fleet");

    expect(screen.getByRole("heading", { name: "Log in" })).toBeInTheDocument();
  });

  it("tells a tourist they have no access and loads nothing", async () => {
    openAs("END_USER");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You do not have access to this page.",
    );
    expect(listHubs).not.toHaveBeenCalled();
    expect(listVehicles).not.toHaveBeenCalled();
  });

  it("forgets an expired session and asks to log in again", async () => {
    openAs("OPERATOR");
    vi.mocked(getProfile).mockRejectedValue(new ApiError(401, {}));
    renderApp("/operator/fleet");

    expect(await screen.findByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(loadSession()).toBeNull();
  });

  it("lists the hubs with the vehicles they hold, and the vehicles with their state", async () => {
    openAs("OPERATOR");

    const hubs = within(await screen.findByRole("table", { name: "Hubs" }));
    expect(hubs.getByRole("row", { name: /hub-03/ })).toHaveTextContent("Canal Central");
    expect(hubs.getByRole("row", { name: /hub-03/ })).toHaveTextContent("1 of 10");
    const vehicles = within(screen.getByRole("table", { name: "Vehicles" }));
    const row = vehicles.getByRole("row", { name: /bike-0009/ });
    expect(row).toHaveTextContent("E-bike");
    expect(row).toHaveTextContent("Deactivated");
    expect(row).toHaveTextContent("hub-03");
    expect(listHubs).toHaveBeenCalledWith(TOKEN);
    expect(listVehicles).toHaveBeenCalledWith(TOKEN);
  });

  it("is open to an admin too", async () => {
    openAs("ADMIN");

    expect(await screen.findByRole("table", { name: "Hubs" })).toBeInTheDocument();
  });

  // ------------------------------------------------------------- hubs

  it("registers a hub and shows it", async () => {
    const hub01 = { ...hub05, id: "id-hub-01", code: "hub-01", name: "Estação", vehiclesHeld: 0 };
    vi.mocked(registerHub).mockResolvedValue(hub01);
    openAs("OPERATOR");
    const user = userEvent.setup();
    const newHub = await form("New hub");
    afterTheChange({ hubs: [hub01, hub03, hub05] });

    await user.type(newHub.getByLabelText("Code"), "hub-01");
    await user.type(newHub.getByLabelText("Name"), "Estação");
    await user.type(newHub.getByLabelText("Latitude"), "40.6443");
    await user.type(newHub.getByLabelText("Longitude"), "-8.6455");
    await user.type(newHub.getByLabelText("Capacity"), "12");
    await user.click(newHub.getByRole("button", { name: "Register hub" }));

    expect(registerHub).toHaveBeenCalledWith(
      { code: "hub-01", name: "Estação", latitude: 40.6443, longitude: -8.6455, capacity: 12 },
      TOKEN,
    );
    expect((await hubRow("hub-01")).getByText("Estação")).toBeInTheDocument();
  });

  it("sends an empty number as missing, so the server names the field", async () => {
    vi.mocked(registerHub).mockRejectedValue(new ApiError(400, { capacity: "must not be null" }));
    openAs("OPERATOR");
    const newHub = await form("New hub");

    await userEvent.setup().click(newHub.getByRole("button", { name: "Register hub" }));

    expect(registerHub).toHaveBeenCalledWith(
      { code: "", name: "", latitude: null, longitude: null, capacity: null },
      TOKEN,
    );
    expect(await newHub.findByText("Enter a capacity of at least 1.")).toBeInTheDocument();
    expect(newHub.getByLabelText("Capacity")).toHaveAccessibleDescription(
      "Enter a capacity of at least 1.",
    );
  });

  it("says when a hub code is already in use", async () => {
    vi.mocked(registerHub).mockRejectedValue(new ApiError(409, { code: "is already in use" }));
    openAs("OPERATOR");
    const newHub = await form("New hub");

    await userEvent.setup().click(newHub.getByRole("button", { name: "Register hub" }));

    expect(await newHub.findByText("This code is already in use.")).toBeInTheDocument();
  });

  it("changes the capacity of a hub", async () => {
    vi.mocked(changeCapacity).mockResolvedValue({ ...hub03, capacity: 9 });
    openAs("OPERATOR");
    const user = userEvent.setup();
    const row = await hubRow("hub-03");
    afterTheChange({ hubs: [{ ...hub03, capacity: 9 }, hub05] });

    await user.clear(row.getByLabelText("Capacity of hub-03"));
    await user.type(row.getByLabelText("Capacity of hub-03"), "9");
    await user.click(row.getByRole("button", { name: "Save" }));

    expect(changeCapacity).toHaveBeenCalledWith(hub03.id, 9, TOKEN);
    expect(await row.findByText("1 of 9")).toBeInTheDocument();
  });

  it("explains a capacity below the vehicles held (BR14)", async () => {
    vi.mocked(changeCapacity).mockRejectedValue(new ApiError(409, { capacity: "holds 1" }));
    openAs("OPERATOR");
    const row = await hubRow("hub-03");

    await userEvent.setup().click(row.getByRole("button", { name: "Save" }));

    expect(await row.findByRole("alert")).toHaveTextContent(
      "The hub holds more vehicles than that.",
    );
  });

  // ------------------------------------------------------------- vehicles

  it("registers a vehicle at the chosen hub", async () => {
    const scooter = vehicle("scooter-0007", "DEACTIVATED", hub05.id);
    vi.mocked(registerVehicle).mockResolvedValue(scooter);
    openAs("OPERATOR");
    const user = userEvent.setup();
    const newVehicle = await form("New vehicle");
    afterTheChange({ vehicles: [bike, scooter] });

    await user.type(newVehicle.getByLabelText("Code"), "scooter-0007");
    await user.selectOptions(newVehicle.getByLabelText("Type"), "E-scooter");
    await user.selectOptions(newVehicle.getByLabelText("Hub"), "hub-05 · Hotel Ria Azul");
    await user.click(newVehicle.getByRole("button", { name: "Register vehicle" }));

    expect(registerVehicle).toHaveBeenCalledWith(
      { code: "scooter-0007", type: "E_SCOOTER", hubId: hub05.id },
      TOKEN,
    );
    expect((await vehicleRow("scooter-0007")).getByText("Deactivated")).toBeInTheDocument();
  });

  it("says when the hub of a new vehicle is full (BR14)", async () => {
    vi.mocked(registerVehicle).mockRejectedValue(new ApiError(409, { hubId: "is full" }));
    openAs("OPERATOR");
    const newVehicle = await form("New vehicle");

    await userEvent.setup().click(newVehicle.getByRole("button", { name: "Register vehicle" }));

    expect(await newVehicle.findByText("This hub is full.")).toBeInTheDocument();
  });

  it("asks for a hub first when there is none", async () => {
    openAs("OPERATOR", [], []);
    const newVehicle = await form("New vehicle");

    expect(newVehicle.getByText("Register a hub first.")).toBeInTheDocument();
    expect(newVehicle.getByRole("button", { name: "Register vehicle" })).toBeDisabled();
  });

  it("offers only the actions allowed in each state (BR16)", async () => {
    openAs(
      "OPERATOR",
      [hub03, hub05],
      [
        vehicle("bike-0001", "DEACTIVATED", hub03.id),
        vehicle("bike-0002", "AVAILABLE", hub03.id),
        vehicle("bike-0003", "IN_TRANSIT", null),
        vehicle("bike-0004", "RESERVED", hub03.id),
        vehicle("bike-0005", "MAINTENANCE", hub03.id),
        vehicle("bike-0006", "RETIRED", null),
      ],
    );

    const actions = async (code: string) =>
      (await vehicleRow(code))
        .getAllByRole("button")
        .map((button) => button.textContent)
        .filter((name) => name !== "History");

    expect(await actions("bike-0001")).toEqual(["Activate", "Retire"]);
    expect(await actions("bike-0002")).toEqual(["Deactivate", "Start move", "Retire"]);
    expect(await actions("bike-0003")).toEqual(["Confirm arrival"]);
    expect(await actions("bike-0004")).toEqual([]);
    expect(await actions("bike-0005")).toEqual(["Retire"]);
    expect(await actions("bike-0006")).toEqual([]);
  });

  it("activates a vehicle and shows its new state", async () => {
    vi.mocked(transition).mockResolvedValue({ ...bike, state: "AVAILABLE" });
    openAs("OPERATOR");
    const row = await vehicleRow("bike-0009");
    afterTheChange({ vehicles: [{ ...bike, state: "AVAILABLE" }] });

    await userEvent.setup().click(row.getByRole("button", { name: "Activate" }));

    expect(transition).toHaveBeenCalledWith(bike.id, "ACTIVATE", TOKEN);
    expect(await row.findByText("Available")).toBeInTheDocument();
  });

  it("confirms the arrival of a vehicle at the chosen hub", async () => {
    const moving = vehicle("bike-0003", "IN_TRANSIT", null);
    vi.mocked(transition).mockResolvedValue({ ...moving, state: "AVAILABLE", hubId: hub05.id });
    openAs("OPERATOR", [hub03, hub05], [moving]);
    const user = userEvent.setup();
    const row = await vehicleRow("bike-0003");

    await user.selectOptions(
      row.getByLabelText("Destination of bike-0003"),
      "hub-05 · Hotel Ria Azul",
    );
    await user.click(row.getByRole("button", { name: "Confirm arrival" }));

    expect(transition).toHaveBeenCalledWith(moving.id, "CONFIRM_ARRIVAL", TOKEN, hub05.id);
  });

  it("asks before retiring, because retirement is final", async () => {
    vi.mocked(transition).mockResolvedValue({ ...bike, state: "RETIRED", hubId: null });
    openAs("OPERATOR");
    const user = userEvent.setup();
    const row = await vehicleRow("bike-0009");

    await user.click(row.getByRole("button", { name: "Retire" }));
    expect(transition).not.toHaveBeenCalled();
    expect(row.getByText("Retire bike-0009 for good?")).toBeInTheDocument();

    await user.click(row.getByRole("button", { name: "Yes, retire" }));
    expect(transition).toHaveBeenCalledWith(bike.id, "RETIRE", TOKEN);
  });

  it("can cancel retiring", async () => {
    openAs("OPERATOR");
    const user = userEvent.setup();
    const row = await vehicleRow("bike-0009");

    await user.click(row.getByRole("button", { name: "Retire" }));
    await user.click(row.getByRole("button", { name: "Cancel" }));

    expect(transition).not.toHaveBeenCalled();
    expect(row.getByRole("button", { name: "Retire" })).toBeInTheDocument();
  });

  it("shows on the row why a change was refused (BR16)", async () => {
    vi.mocked(transition).mockRejectedValue(new ApiError(409, { action: "cannot ACTIVATE" }));
    openAs("OPERATOR");
    const row = await vehicleRow("bike-0009");

    await userEvent.setup().click(row.getByRole("button", { name: "Activate" }));

    expect(await row.findByRole("alert")).toHaveTextContent(
      "This change is not allowed in the vehicle's current state.",
    );
  });

  it("says when the destination hub is full (BR14)", async () => {
    const moving = vehicle("bike-0003", "IN_TRANSIT", null);
    vi.mocked(transition).mockRejectedValue(new ApiError(409, { hubId: "is full" }));
    openAs("OPERATOR", [hub03, hub05], [moving]);
    const row = await vehicleRow("bike-0003");

    await userEvent.setup().click(row.getByRole("button", { name: "Confirm arrival" }));

    expect(await row.findByRole("alert")).toHaveTextContent("This hub is full.");
  });

  it("shows the state history of a vehicle", async () => {
    vi.mocked(vehicleHistory).mockResolvedValue([
      { from: null, to: "DEACTIVATED", at: "2026-11-20T10:00:00Z" },
      { from: "DEACTIVATED", to: "AVAILABLE", at: "2026-11-20T10:05:00Z" },
    ]);
    openAs("OPERATOR");
    const row = await vehicleRow("bike-0009");

    await userEvent.setup().click(row.getByRole("button", { name: "History" }));

    const history = within(await row.findByRole("list", { name: "History of bike-0009" }));
    expect(history.getAllByRole("listitem").map((item) => item.textContent)).toEqual([
      expect.stringContaining("New → Deactivated"),
      expect.stringContaining("Deactivated → Available"),
    ]);
    expect(vehicleHistory).toHaveBeenCalledWith(bike.id, TOKEN);
  });
});
