import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";
import { listHubs, searchHubVehicles, type Hub, type HubVehicle } from "../api/fleet";
import {
  bookForLater,
  listVehicleTypes,
  startRide,
  type Booking,
  type VehicleTypeDeposit,
} from "../api/bookings";
import { listPasses, type Pass } from "../api/passes";
import { loadSession, saveSession } from "../auth/session";
import { renderApp } from "../test/renderApp";

vi.mock("../api/fleet");
vi.mock("../api/bookings");
vi.mock("../api/passes");

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

const DEPOSITS: VehicleTypeDeposit[] = [
  { type: "E_BIKE", deposit: "30.00", currency: "EUR" },
  { type: "E_SCOOTER", deposit: "50.00", currency: "EUR" },
];

function started(overrides: Partial<Booking> = {}): Booking {
  return {
    id: "booking-1",
    state: "ACTIVE",
    vehicleId: "id-bike-0001",
    passId: "pass-1",
    startHubId: hub03.id,
    plannedStart: "2026-11-26T10:00:00Z",
    plannedEnd: "2026-11-26T11:00:00Z",
    startWindowEndsAt: null,
    startedAt: "2026-11-26T10:00:00Z",
    cancelReason: null,
    deposit: "30.00",
    currency: "EUR",
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
  // Every search page reads the deposits when it opens.
  vi.mocked(listVehicleTypes).mockReset().mockResolvedValue(DEPOSITS);
  vi.mocked(startRide).mockReset();
  vi.mocked(bookForLater).mockReset();
  vi.mocked(listPasses).mockReset().mockResolvedValue([]);
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

// ------------------------------------------------------------- starting a ride (US04)

const startPanel = () => screen.findByRole("region", { name: "Start a ride" });

async function selectVehicle(code: string) {
  const user = userEvent.setup();
  await user.click((await row(code)).getByRole("button", { name: "Select" }));
  return user;
}

describe("starting a ride from the search (US04)", () => {
  it("shows the deposit of the selected vehicle's type before the tourist confirms", async () => {
    openSearch([hub03], [found("bike-0001"), found("scooter-0001", { type: "E_SCOOTER" })]);

    const user = await selectVehicle("bike-0001");
    expect(
      within(await startPanel()).getByText("Deposit held during the ride: 30.00 EUR"),
    ).toBeInTheDocument();

    await user.click((await row("scooter-0001")).getByRole("button", { name: "Select" }));
    expect(
      within(await startPanel()).getByText("Deposit held during the ride: 50.00 EUR"),
    ).toBeInTheDocument();
    expect(startRide).not.toHaveBeenCalled();
  });

  it("offers rides of up to 4 hours, one hour by default", async () => {
    openSearch([hub03], [found("bike-0001")]);
    await selectVehicle("bike-0001");

    const duration = within(await startPanel()).getByLabelText("Ride duration");
    expect(within(duration).getAllByRole("option").map((o) => o.textContent)).toEqual([
      "30 minutes",
      "1 hour",
      "2 hours",
      "3 hours",
      "4 hours",
    ]);
    expect(duration).toHaveValue("60");
  });

  it("starts with the ride length chosen in the search", async () => {
    openSearch([hub03], [found("bike-0001")]);
    const user = userEvent.setup();
    await user.selectOptions(await screen.findByLabelText("Ride length"), "120");
    await waitFor(() =>
      expect(searchHubVehicles).toHaveBeenLastCalledWith(
        hub03.id,
        { type: null, minutes: 120 },
        TOKEN,
      ),
    );

    await user.click((await row("bike-0001")).getByRole("button", { name: "Select" }));

    expect(within(await startPanel()).getByLabelText("Ride duration")).toHaveValue("120");
  });

  it("starts the ride with the vehicle, the planned end and the chosen test card", async () => {
    openSearch([hub03], [found("bike-0001")]);
    vi.mocked(startRide).mockResolvedValue(started());
    const user = await selectVehicle("bike-0001");
    const panel = within(await startPanel());
    await user.selectOptions(panel.getByLabelText("Ride duration"), "120");
    await user.click(panel.getByRole("radio", { name: "Payment declined" }));
    const before = Date.now();

    await user.click(panel.getByRole("button", { name: "Start ride" }));

    await waitFor(() => expect(startRide).toHaveBeenCalledOnce());
    const [ride, token] = vi.mocked(startRide).mock.calls[0];
    expect(token).toBe(TOKEN);
    expect(ride.vehicleId).toBe("id-bike-0001");
    expect(ride.paymentToken).toBe("tok_decline");
    const minutes = (Date.parse(ride.plannedEnd) - before) / 60_000;
    expect(minutes).toBeGreaterThanOrEqual(120);
    expect(minutes).toBeLessThan(121);
  });

  it("shows the ride once it has started", async () => {
    openSearch([hub03], [found("bike-0001")]);
    vi.mocked(startRide).mockResolvedValue(started());
    const user = await selectVehicle("bike-0001");

    await user.click(within(await startPanel()).getByRole("button", { name: "Start ride" }));

    const ride = within(await screen.findByRole("region", { name: "Your ride has started" }));
    expect(ride.getByText("bike-0001")).toBeInTheDocument();
    expect(ride.getByText("hub-03 · Canal Central")).toBeInTheDocument();
    expect(ride.getByText("30.00 EUR")).toBeInTheDocument();
    expect(ride.getByText("The vehicle is being unlocked.")).toBeInTheDocument();
  });

  it.each([
    [
      409,
      { vehicleId: "is not available" },
      "This vehicle was just taken or cannot do this ride. Choose another one.",
    ],
    [
      409,
      { booking: "you already have a booking in that period" },
      "You already have a ride in progress.",
    ],
    [
      400,
      { plannedEnd: "is after the end of your pass" },
      "This ride is too long for the rules or for your pass. Choose a shorter one.",
    ],
    [
      402,
      { deposit: "was declined" },
      "Your deposit was declined. No ride was started and nothing was held.",
    ],
    [
      504,
      { deposit: "got no answer in time; try again" },
      "The deposit did not answer in time. No ride was started; please try again.",
    ],
  ])("explains a refusal (%i %o) in words for tourists", async (status, errors, message) => {
    openSearch([hub03], [found("bike-0001")]);
    vi.mocked(startRide).mockRejectedValue(new ApiError(status, errors));
    const user = await selectVehicle("bike-0001");

    await user.click(within(await startPanel()).getByRole("button", { name: "Start ride" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
  });

  it("offers to buy a pass when the tourist has no valid one", async () => {
    openSearch([hub03], [found("bike-0001")]);
    vi.mocked(startRide).mockRejectedValue(new ApiError(409, { pass: "you have no valid pass" }));
    const user = await selectVehicle("bike-0001");

    await user.click(within(await startPanel()).getByRole("button", { name: "Start ride" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You need a valid pass that allows this vehicle type.",
    );
    expect(screen.getByRole("link", { name: "Buy a pass" })).toHaveAttribute("href", "/passes/buy");
  });

  it("searches the hub again when the vehicle was just taken", async () => {
    openSearch([hub03], [found("bike-0001")]);
    vi.mocked(startRide).mockRejectedValue(new ApiError(409, { vehicleId: "is not available" }));
    const user = await selectVehicle("bike-0001");
    const searchesBefore = vi.mocked(searchHubVehicles).mock.calls.length;

    await user.click(within(await startPanel()).getByRole("button", { name: "Start ride" }));

    await waitFor(() =>
      expect(vi.mocked(searchHubVehicles).mock.calls.length).toBeGreaterThan(searchesBefore),
    );
  });

  it("asks to log in again when the token is refused while starting", async () => {
    openSearch([hub03], [found("bike-0001")]);
    vi.mocked(startRide).mockRejectedValue(new ApiError(401, {}));
    const user = await selectVehicle("bike-0001");

    await user.click(within(await startPanel()).getByRole("button", { name: "Start ride" }));

    expect(await screen.findByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(loadSession()).toBeNull();
  });

  it("does not offer to start while the deposits are unknown", async () => {
    // Set after the beforeEach default; openSearch does not change it.
    vi.mocked(listVehicleTypes).mockRejectedValue(new ApiError(500, {}));
    openSearch([hub03], [found("bike-0001")]);
    await selectVehicle("bike-0001");

    const panel = within(await startPanel());
    expect(
      panel.getByText("The deposit cannot be shown right now. Please try again later."),
    ).toBeInTheDocument();
    expect(panel.getByRole("button", { name: "Start ride" })).toBeDisabled();
  });
});

describe("booking a vehicle for later (US05)", () => {
  const MINUTE = 60_000;

  /** A datetime-local value ("YYYY-MM-DDTHH:mm") in the browser's zone, as the picker uses. */
  function localInput(at: Date): string {
    const pad = (n: number) => String(n).padStart(2, "0");
    return (
      `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())}` +
      `T${pad(at.getHours())}:${pad(at.getMinutes())}`
    );
  }

  function dayPass(validTo: Date): Pass {
    return {
      id: "pass-1",
      passTypeId: "type-day",
      passTypeCode: "DAY",
      price: "10.00",
      currency: "EUR",
      validFrom: new Date(Date.now() - 60 * MINUTE).toISOString(),
      validTo: validTo.toISOString(),
    };
  }

  async function chooseLater(validTo = new Date(Date.now() + 20 * 60 * MINUTE)) {
    const user = userEvent.setup();
    vi.mocked(listPasses).mockResolvedValue([dayPass(validTo)]);
    openSearch([hub03], [found("bike-0001")]);
    await user.click((await row("bike-0001")).getByRole("button", { name: "Select" }));
    await user.click(screen.getByRole("radio", { name: "Later" }));
    return user;
  }

  it("books the vehicle for the chosen start, length and test card", async () => {
    const user = await chooseLater();
    const start = new Date(Date.now() + 2 * 60 * MINUTE);
    start.setSeconds(0, 0);
    vi.mocked(bookForLater).mockResolvedValue(
      started({
        state: "RESERVED",
        startedAt: null,
        plannedStart: start.toISOString(),
        startWindowEndsAt: new Date(start.getTime() + 15 * MINUTE).toISOString(),
      }),
    );

    fireEvent.change(await screen.findByLabelText("Start at"), {
      target: { value: localInput(start) },
    });
    await user.click(screen.getByRole("button", { name: "Book for later" }));

    expect(bookForLater).toHaveBeenCalledWith(
      {
        vehicleId: "id-bike-0001",
        plannedStart: start.toISOString(),
        plannedEnd: new Date(start.getTime() + 60 * MINUTE).toISOString(),
        paymentToken: "tok_approve",
      },
      TOKEN,
    );
    expect(startRide).not.toHaveBeenCalled();
    expect(await screen.findByRole("heading", { name: "Your vehicle is reserved" })).toBeVisible();
    expect(screen.getByRole("link", { name: "My bookings" })).toHaveAttribute("href", "/bookings");
  });

  /** BR4: the latest start is the end of the tourist's last pass minus the ride. */
  it("limits the start to the validity of the tourist's passes", async () => {
    const validTo = new Date(Date.now() + 10 * 60 * MINUTE);
    validTo.setSeconds(0, 0);
    await chooseLater(validTo);

    const startAt = await screen.findByLabelText("Start at");
    expect(startAt).toHaveAttribute("max", localInput(new Date(validTo.getTime() - 60 * MINUTE)));
  });

  it("offers to buy a pass when no pass covers a later ride", async () => {
    vi.mocked(listPasses).mockResolvedValue([]);
    const user = userEvent.setup();
    openSearch([hub03], [found("bike-0001")]);
    await user.click((await row("bike-0001")).getByRole("button", { name: "Select" }));
    await user.click(screen.getByRole("radio", { name: "Later" }));

    expect(await screen.findByText("You need a pass that is valid when the ride starts.")).toBeVisible();
    expect(screen.getByRole("button", { name: "Book for later" })).toBeDisabled();
  });

  it("explains a start that the API does not accept as in the future", async () => {
    const user = await chooseLater();
    vi.mocked(bookForLater).mockRejectedValue(
      new ApiError(400, { plannedStart: "must be in the future" }),
    );

    fireEvent.change(await screen.findByLabelText("Start at"), {
      target: { value: localInput(new Date(Date.now() + 2 * 60 * MINUTE)) },
    });
    await user.click(screen.getByRole("button", { name: "Book for later" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Choose a start time in the future.");
  });
});

