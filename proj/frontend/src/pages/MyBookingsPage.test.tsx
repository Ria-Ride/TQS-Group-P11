import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";
import { cancelBooking, listBookings, startBooking, type Booking } from "../api/bookings";
import { loadSession, saveSession } from "../auth/session";
import { renderApp } from "../test/renderApp";

vi.mock("../api/bookings");

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
    cancelReason: null,
    deposit: "30.00",
    currency: "EUR",
    ...overrides,
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
