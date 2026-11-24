import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";
import { listHubs, searchHubVehicles, type Hub, type HubVehicle } from "../api/fleet";
import { loadSession, saveSession } from "../auth/session";
import { renderApp } from "../test/renderApp";

vi.mock("../api/fleet");

const TOKEN = "token-for-ana";
const USER_ID = "4b1d6f2e-8a3c-4e5b-9d7f-1a2b3c4d5e6f";

const hub03: Hub = {
  id: "2b3c4d5e-0000-4000-8000-000000000103",
  code: "hub-03",
  name: "Canal Central",
  latitude: 40.6405,
  longitude: -8.6538,
  capacity: 10,
  vehiclesHeld: 2,
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

function found(code: string, overrides: Partial<HubVehicle> = {}): HubVehicle {
  return {
    id: `id-${code}`,
    code,
    type: "E_BIKE",
    state: "AVAILABLE",
    batteryPercent: 80,
    requiredBatteryPercent: 20,
    bookable: true,
    unbookableReason: null,
    ...overrides,
  };
}

function openSearch(hubs: Hub[] = [hub03, hub05], vehicles: HubVehicle[] = []) {
  saveSession({ userId: USER_ID, accessToken: TOKEN });
  vi.mocked(listHubs).mockResolvedValue(hubs);
  vi.mocked(searchHubVehicles).mockResolvedValue(vehicles);
  renderApp("/vehicles/search");
}

const vehiclesTable = () => screen.findByRole("table", { name: "Vehicles at this hub" });

async function row(code: string) {
  const table = within(await vehiclesTable());
  return within(await table.findByRole("row", { name: new RegExp(code) }));
}

beforeEach(() => {
  vi.mocked(listHubs).mockReset();
  vi.mocked(searchHubVehicles).mockReset();
  sessionStorage.clear();
});

describe("vehicle search page", () => {
  it("sends a visitor without a session to the login", () => {
    renderApp("/vehicles/search");

    expect(screen.getByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(listHubs).not.toHaveBeenCalled();
  });

  it("forgets an expired session and asks to log in again", async () => {
    saveSession({ userId: USER_ID, accessToken: TOKEN });
    vi.mocked(listHubs).mockRejectedValue(new ApiError(401, {}));
    renderApp("/vehicles/search");

    expect(await screen.findByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(loadSession()).toBeNull();
  });

  it("offers every hub and searches the first one", async () => {
    openSearch([hub03, hub05], [found("bike-0001")]);

    const hubSelect = await screen.findByLabelText("Hub");
    expect(within(hubSelect).getAllByRole("option").map((o) => o.textContent)).toEqual([
      "hub-03 · Canal Central",
      "hub-05 · Hotel Ria Azul",
    ]);
    await vehiclesTable();
    expect(searchHubVehicles).toHaveBeenCalledWith(
      hub03.id,
      { type: null, minutes: null },
      TOKEN,
    );
  });

  it("says so when there is no hub at all", async () => {
    openSearch([], []);

    expect(await screen.findByText("There are no hubs yet.")).toBeInTheDocument();
    expect(searchHubVehicles).not.toHaveBeenCalled();
  });

  // ------------------------------------------------------------- what the tourist sees

  it("shows each vehicle with its type and battery level", async () => {
    openSearch(
      [hub03],
      [
        found("bike-0001", { batteryPercent: 80 }),
        found("scooter-0001", { type: "E_SCOOTER", batteryPercent: 55 }),
      ],
    );

    const bike = await row("bike-0001");
    expect(bike.getByText("E-bike")).toBeInTheDocument();
    expect(bike.getByText("80%")).toBeInTheDocument();
    const scooter = await row("scooter-0001");
    expect(scooter.getByText("E-scooter")).toBeInTheDocument();
    expect(scooter.getByText("55%")).toBeInTheDocument();
  });

  it("marks a bookable vehicle and lets the tourist select it", async () => {
    openSearch([hub03], [found("bike-0001")]);
    const user = userEvent.setup();

    const bike = await row("bike-0001");
    expect(bike.getByText("Bookable")).toBeInTheDocument();
    await user.click(bike.getByRole("button", { name: "Select" }));

    expect(await screen.findByText("Selected: bike-0001")).toBeInTheDocument();
    expect(
      (await row("bike-0001")).getByRole("button", { name: "Selected" }),
    ).toHaveAttribute("aria-pressed", "true");
  });

  // BR1
  it("shows a vehicle below the minimum battery as not bookable, and it cannot be selected", async () => {
    openSearch(
      [hub03],
      [
        found("bike-0019", {
          batteryPercent: 19,
          bookable: false,
          unbookableReason: "LOW_BATTERY",
        }),
      ],
    );

    const bike = await row("bike-0019");
    expect(bike.getByText("Battery too low (needs at least 20%)")).toBeInTheDocument();
    expect(bike.getByRole("button", { name: "Select" })).toBeDisabled();
  });

  it("says how much battery the ride needs when the vehicle is not enough for it", async () => {
    openSearch(
      [hub03],
      [
        found("bike-0001", {
          batteryPercent: 29,
          requiredBatteryPercent: 30,
          bookable: false,
          unbookableReason: "INSUFFICIENT_FOR_DURATION",
        }),
      ],
    );

    const bike = await row("bike-0001");
    expect(bike.getByText("Not enough battery for this ride (needs 30%)")).toBeInTheDocument();
    expect(bike.getByRole("button", { name: "Select" })).toBeDisabled();
  });

  it("says when the ride is longer than any battery can do", async () => {
    openSearch(
      [hub03],
      [
        found("bike-0001", {
          batteryPercent: 100,
          requiredBatteryPercent: 140,
          bookable: false,
          unbookableReason: "INSUFFICIENT_FOR_DURATION",
        }),
      ],
    );

    expect(
      (await row("bike-0001")).getByText("This ride is too long for one battery charge"),
    ).toBeInTheDocument();
  });

  it("shows an unknown battery level as unknown and not bookable", async () => {
    openSearch(
      [hub03],
      [
        found("bike-0001", {
          batteryPercent: null,
          bookable: false,
          unbookableReason: "NO_BATTERY_READING",
        }),
      ],
    );

    const bike = await row("bike-0001");
    expect(bike.getByText("Unknown")).toBeInTheDocument();
    expect(bike.getByText("Battery level unknown")).toBeInTheDocument();
    expect(bike.getByRole("button", { name: "Select" })).toBeDisabled();
  });

  // BR2
  it("shows a vehicle that is not Available with its state, and it cannot be selected", async () => {
    openSearch(
      [hub03],
      [
        found("bike-0002", {
          state: "RESERVED",
          bookable: false,
          unbookableReason: "NOT_AVAILABLE",
        }),
      ],
    );

    const bike = await row("bike-0002");
    expect(bike.getByText("Not available (Reserved)")).toBeInTheDocument();
    expect(bike.getByRole("button", { name: "Select" })).toBeDisabled();
  });

  // ------------------------------------------------------------- empty state

  it("shows an empty-state message for a hub with no vehicle", async () => {
    openSearch([hub03], []);

    expect(
      await screen.findByText("No vehicle is available at this hub right now."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: "Vehicles at this hub" })).not.toBeInTheDocument();
  });

  it("shows the same message when no vehicle of the hub can be booked, and still lists them", async () => {
    openSearch(
      [hub03],
      [found("bike-0002", { state: "RESERVED", bookable: false, unbookableReason: "NOT_AVAILABLE" })],
    );

    expect(
      await screen.findByText("No vehicle is available at this hub right now."),
    ).toBeInTheDocument();
    expect(await row("bike-0002")).toBeTruthy();
  });

  it("shows no empty-state message while a vehicle can be booked", async () => {
    openSearch([hub03], [found("bike-0001")]);

    await vehiclesTable();
    expect(
      screen.queryByText("No vehicle is available at this hub right now."),
    ).not.toBeInTheDocument();
  });

  // ------------------------------------------------------------- filters

  it("searches the chosen hub", async () => {
    openSearch([hub03, hub05], []);
    const user = userEvent.setup();

    await user.selectOptions(await screen.findByLabelText("Hub"), hub05.id);

    await waitFor(() =>
      expect(searchHubVehicles).toHaveBeenLastCalledWith(
        hub05.id,
        { type: null, minutes: null },
        TOKEN,
      ),
    );
  });

  it("filters by vehicle type and goes back to every type", async () => {
    openSearch([hub03], [found("bike-0001")]);
    const user = userEvent.setup();
    const typeSelect = await screen.findByLabelText("Vehicle type");
    expect(
      within(typeSelect).getAllByRole("option").map((option) => option.textContent),
    ).toEqual(["All types", "E-bike", "E-scooter"]);

    await user.selectOptions(typeSelect, "E_SCOOTER");
    await waitFor(() =>
      expect(searchHubVehicles).toHaveBeenLastCalledWith(
        hub03.id,
        { type: "E_SCOOTER", minutes: null },
        TOKEN,
      ),
    );

    await user.selectOptions(typeSelect, "");
    await waitFor(() =>
      expect(searchHubVehicles).toHaveBeenLastCalledWith(
        hub03.id,
        { type: null, minutes: null },
        TOKEN,
      ),
    );
  });

  it("searches for the length of the ride the tourist picks", async () => {
    openSearch([hub03], [found("bike-0001")]);
    const user = userEvent.setup();
    const ride = await screen.findByLabelText("Ride length");
    expect(
      within(ride).getAllByRole("option").map((option) => option.textContent),
    ).toEqual([
      "Not sure yet",
      "Up to 30 minutes",
      "Up to 1 hour",
      "Up to 2 hours",
      "Up to 3 hours",
      "Up to 4 hours",
      "Up to 8 hours",
    ]);

    await user.selectOptions(ride, "60");

    await waitFor(() =>
      expect(searchHubVehicles).toHaveBeenLastCalledWith(
        hub03.id,
        { type: null, minutes: 60 },
        TOKEN,
      ),
    );
  });

  it("clears the selection when the results change", async () => {
    openSearch([hub03], [found("bike-0001")]);
    const user = userEvent.setup();
    await user.click((await row("bike-0001")).getByRole("button", { name: "Select" }));
    expect(await screen.findByText("Selected: bike-0001")).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Ride length"), "120");

    await waitFor(() => expect(screen.queryByText("Selected: bike-0001")).not.toBeInTheDocument());
  });

  // ------------------------------------------------------------- failures

  it("shows an error when the search fails", async () => {
    saveSession({ userId: USER_ID, accessToken: TOKEN });
    vi.mocked(listHubs).mockResolvedValue([hub03]);
    vi.mocked(searchHubVehicles).mockRejectedValue(new ApiError(500, {}));
    renderApp("/vehicles/search");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Something went wrong. Please try again.",
    );
  });

  it("asks to log in again when the token is refused during a search", async () => {
    saveSession({ userId: USER_ID, accessToken: TOKEN });
    vi.mocked(listHubs).mockResolvedValue([hub03]);
    vi.mocked(searchHubVehicles).mockRejectedValue(new ApiError(401, {}));
    renderApp("/vehicles/search");

    expect(await screen.findByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(loadSession()).toBeNull();
  });
});
