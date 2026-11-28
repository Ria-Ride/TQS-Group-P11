import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";
import {
  cancelBooking,
  endRide,
  listBookings,
  startBooking,
  type Booking,
} from "../api/bookings";
import { listHubs, type Hub } from "../api/fleet";
import { loadSession, saveSession } from "../auth/session";
import { renderApp } from "../test/renderApp";

vi.mock("../api/bookings", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/bookings")>()),
  cancelBooking: vi.fn(),
  endRide: vi.fn(),
  listBookings: vi.fn(),
  startBooking: vi.fn(),
}));
vi.mock("../api/fleet");

const TOKEN = "token-for-ana";
const USER_ID = "4b1d6f2e-8a3c-4e5b-9d7f-1a2b3c4d5e6f";
const MINUTE = 60_000;

/** An instant this many minutes from now, as the API sends it. */
function inMinutes(minutes: number): string {
  return new Date(Date.now() + minutes * MINUTE).toISOString();
}

/** A reservation starting in `startsIn` minutes, with a 15-minute start window (BR7). */
function reserved(id: string, startsIn: number, overrides: Partial<Booking> = {}): Booking {
  return {
    id,
    state: "RESERVED",
    vehicleId: `vehicle-${id}`,
    passId: "pass-1",
    startHubId: "hub-03",
    plannedStart: inMinutes(startsIn),
    plannedEnd: inMinutes(startsIn + 60),
    startWindowEndsAt: inMinutes(startsIn + 15),
    startedAt: null,
    fullHubAttemptAt: null,
    endedAt: null,
    endHubId: null,
    overtimeCharge: null,
    depositCaptured: null,
    depositReleased: null,
    cancelReason: null,
    deposit: "30.00",
    currency: "EUR",
    ...overrides,
  };
}

function hub(code: string, name: string): Hub {
  return {
    id: `id-${code}`,
    code,
    name,
    latitude: 40.64,
    longitude: -8.65,
    capacity: 10,
    vehiclesHeld: 0,
  };
}

const HUBS = [hub("hub-03", "Canal Central"), hub("hub-05", "Hotel Ria Azul"), hub("hub-07", "Forum")];

/** A ride that started an hour ago, planned for an hour. */
function riding(id: string, overrides: Partial<Booking> = {}): Booking {
  return reserved(id, -60, {
    state: "ACTIVE",
    startedAt: inMinutes(-60),
    startWindowEndsAt: null,
    plannedEnd: inMinutes(0),
    ...overrides,
  });
}

function ended(booking: Booking, settlement: Partial<Booking>): Booking {
  return {
    ...booking,
    state: "COMPLETED",
    endedAt: inMinutes(0),
    endHubId: "id-hub-05",
    overtimeCharge: "0.00",
    depositCaptured: "0.00",
    depositReleased: "30.00",
    ...settlement,
  };
}

function openMyBookings(bookings: Booking[]) {
  saveSession({ userId: USER_ID, accessToken: TOKEN });
  vi.mocked(listBookings).mockResolvedValue(bookings);
  renderApp("/bookings");
}

async function bookingCard(id: string) {
  return within(await screen.findByRole("article", { name: `Booking ${id}` }));
}

beforeEach(() => {
  vi.mocked(listBookings).mockReset();
  vi.mocked(startBooking).mockReset();
  vi.mocked(cancelBooking).mockReset();
  vi.mocked(endRide).mockReset();
  vi.mocked(listHubs).mockReset().mockResolvedValue(HUBS);
  sessionStorage.clear();
});

describe("my bookings page (US05)", () => {
  it("sends a visitor without a session to the login", () => {
    renderApp("/bookings");

    expect(screen.getByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(listBookings).not.toHaveBeenCalled();
  });

  it("asks to log in again when the token is refused", async () => {
    saveSession({ userId: USER_ID, accessToken: TOKEN });
    vi.mocked(listBookings).mockRejectedValue(new ApiError(401, {}));
    renderApp("/bookings");

    expect(await screen.findByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(loadSession()).toBeNull();
  });

  it("says so when the tourist has no booking", async () => {
    openMyBookings([]);

    expect(await screen.findByText("You have no bookings yet.")).toBeInTheDocument();
  });

  it("shows a reservation with its deposit and when it can be started", async () => {
    openMyBookings([reserved("b-1", 60)]);

    const card = await bookingCard("b-1");
    expect(card.getByText("Reserved")).toBeInTheDocument();
    expect(card.getByText("30.00 EUR")).toBeInTheDocument();
    expect(card.getByText(/Start it between/)).toBeInTheDocument();
  });

  /** BR7: the start action is only offered inside the window. */
  it("does not let the tourist start a reservation before its planned start", async () => {
    openMyBookings([reserved("b-1", 60)]);

    const card = await bookingCard("b-1");
    expect(card.getByRole("button", { name: "Start ride" })).toBeDisabled();
  });

  it("does not let the tourist start a reservation after its start window", async () => {
    openMyBookings([reserved("b-1", -16)]);

    const card = await bookingCard("b-1");
    expect(card.getByRole("button", { name: "Start ride" })).toBeDisabled();
  });

  it("starts a reservation inside its window and shows the ride", async () => {
    const user = userEvent.setup();
    const booking = reserved("b-1", -1);
    openMyBookings([booking]);
    vi.mocked(startBooking).mockResolvedValue({
      ...booking,
      state: "ACTIVE",
      startedAt: inMinutes(0),
      startWindowEndsAt: null,
    });

    const card = await bookingCard("b-1");
    await user.click(card.getByRole("button", { name: "Start ride" }));

    expect(startBooking).toHaveBeenCalledWith("b-1", TOKEN);
    expect(await card.findByText("In progress")).toBeInTheDocument();
    expect(card.queryByRole("button", { name: "Cancel booking" })).not.toBeInTheDocument();
  });

  it("cancels a reservation and says the deposit was given back", async () => {
    const user = userEvent.setup();
    const booking = reserved("b-1", 60);
    openMyBookings([booking]);
    vi.mocked(cancelBooking).mockResolvedValue({
      ...booking,
      state: "CANCELLED",
      startWindowEndsAt: null,
      cancelReason: "cancelled by the tourist",
    });

    const card = await bookingCard("b-1");
    await user.click(card.getByRole("button", { name: "Cancel booking" }));

    expect(cancelBooking).toHaveBeenCalledWith("b-1", TOKEN);
    expect(await card.findByText("Cancelled")).toBeInTheDocument();
    expect(card.getByText("Your deposit of 30.00 EUR was given back.")).toBeInTheDocument();
  });

  it("explains a start the API refuses because the window has passed", async () => {
    const user = userEvent.setup();
    openMyBookings([reserved("b-1", -1)]);
    vi.mocked(startBooking).mockRejectedValue(
      new ApiError(409, { booking: "cannot be started after its start window" }),
    );

    const card = await bookingCard("b-1");
    await user.click(card.getByRole("button", { name: "Start ride" }));

    expect(await card.findByRole("alert")).toHaveTextContent(
      "This booking can no longer be started or cancelled. Refresh to see its state.",
    );
  });

  it("offers no action on bookings that are over", async () => {
    openMyBookings([
      reserved("b-2", -120, { state: "EXPIRED", startWindowEndsAt: null }),
    ]);

    const card = await bookingCard("b-2");
    expect(card.getByText("Expired")).toBeInTheDocument();
    expect(card.queryByRole("button")).not.toBeInTheDocument();
  });
});

describe("ending a ride from my bookings (US06)", () => {
  it("offers to end an active ride at any hub", async () => {
    openMyBookings([riding("b-1")]);

    const card = await bookingCard("b-1");
    const where = await card.findByLabelText("End at hub");
    expect(within(where).getAllByRole("option").map((o) => o.textContent)).toEqual([
      "hub-03 · Canal Central",
      "hub-05 · Hotel Ria Azul",
      "hub-07 · Forum",
    ]);
    expect(card.getByRole("button", { name: "End ride" })).toBeEnabled();
  });

  it("ends the ride at the chosen hub and shows what the deposit paid", async () => {
    const user = userEvent.setup();
    const booking = riding("b-1");
    openMyBookings([booking]);
    vi.mocked(endRide).mockResolvedValue(
      ended(booking, { overtimeCharge: "4.00", depositCaptured: "4.00", depositReleased: "26.00" }),
    );

    const card = await bookingCard("b-1");
    await user.selectOptions(await card.findByLabelText("End at hub"), "id-hub-05");
    await user.click(card.getByRole("button", { name: "End ride" }));

    expect(endRide).toHaveBeenCalledWith("b-1", "id-hub-05", TOKEN);
    expect(await card.findByText("Ended")).toBeInTheDocument();
    expect(card.getByText("4.00 EUR", { selector: "dd.overtime" })).toBeInTheDocument();
    expect(card.getByText("4.00 EUR", { selector: "dd.captured" })).toBeInTheDocument();
    expect(card.getByText("26.00 EUR", { selector: "dd.released" })).toBeInTheDocument();
  });

  /** BR14, BR15: the full hub's suggestions can be used at once. */
  it("shows the hubs with room when the chosen hub is full, and ends at one of them", async () => {
    const user = userEvent.setup();
    const booking = riding("b-1");
    openMyBookings([booking]);
    vi.mocked(endRide)
      .mockRejectedValueOnce(
        new ApiError(
          409,
          { hubId: "is full" },
          {
            nearbyHubs: [
              { id: "id-hub-05", code: "hub-05", name: "Hotel Ria Azul", freeSlots: 6, distanceMeters: 245 },
            ],
          },
        ),
      )
      .mockResolvedValueOnce(ended(booking, {}));

    const card = await bookingCard("b-1");
    await user.selectOptions(await card.findByLabelText("End at hub"), "id-hub-07");
    await user.click(card.getByRole("button", { name: "End ride" }));

    expect(await card.findByRole("alert")).toHaveTextContent(
      "This hub is full. End at a nearby hub with room",
    );
    await user.click(card.getByRole("button", { name: "End at hub-05 · Hotel Ria Azul (6 free, 245 m)" }));

    expect(endRide).toHaveBeenLastCalledWith("b-1", "id-hub-05", TOKEN);
    expect(await card.findByText("Ended")).toBeInTheDocument();
  });

  it("explains a vehicle that could not be locked, and the ride goes on", async () => {
    const user = userEvent.setup();
    openMyBookings([riding("b-1")]);
    vi.mocked(endRide).mockRejectedValue(
      new ApiError(409, { vehicleId: "could not be locked; check that it is parked in the hub" }),
    );

    const card = await bookingCard("b-1");
    await user.click(await card.findByRole("button", { name: "End ride" }));

    expect(await card.findByRole("alert")).toHaveTextContent(
      "The vehicle could not be locked. Park it inside the hub and try again.",
    );
    expect(card.getByText("In progress")).toBeInTheDocument();
  });

  it("explains a vehicle that did not answer", async () => {
    const user = userEvent.setup();
    openMyBookings([riding("b-1")]);
    vi.mocked(endRide).mockRejectedValue(new ApiError(504, { vehicleId: "did not answer" }));

    const card = await bookingCard("b-1");
    await user.click(await card.findByRole("button", { name: "End ride" }));

    expect(await card.findByRole("alert")).toHaveTextContent(
      "The vehicle did not answer. Try again in a moment.",
    );
  });

  it("shows the charge of a ride that has ended", async () => {
    openMyBookings([
      ended(riding("b-2"), { overtimeCharge: "34.00", depositCaptured: "30.00", depositReleased: "0.00" }),
    ]);

    const card = await bookingCard("b-2");
    expect(card.getByText("34.00 EUR", { selector: "dd.overtime" })).toBeInTheDocument();
    expect(card.getByText("30.00 EUR", { selector: "dd.captured" })).toBeInTheDocument();
    expect(card.getByText("0.00 EUR", { selector: "dd.released" })).toBeInTheDocument();
    expect(card.queryByRole("button")).not.toBeInTheDocument();
  });
});

