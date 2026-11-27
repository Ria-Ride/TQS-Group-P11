import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getProfile } from "../api/auth";
import {
  changeDeposit,
  changeRules,
  createPassType,
  getRules,
  issueStayCode,
  listHotels,
  listRulesVersions,
  listStayCodes,
  registerHotel,
  updatePassType,
  type Hotel,
  type RulesOfUse,
  type StayCode,
} from "../api/backoffice";
import { ApiError } from "../api/client";
import { changeCapacity, listHubs, type Hub } from "../api/fleet";
import { listPassTypes, type PassType } from "../api/passes";
import { loadSession, saveSession } from "../auth/session";
import { renderApp } from "../test/renderApp";

vi.mock("../api/auth");
vi.mock("../api/backoffice");
vi.mock("../api/fleet");
vi.mock("../api/passes");

const TOKEN = "token-for-mara";
const USER_ID = "4b1d6f2e-8a3c-4e5b-9d7f-1a2b3c4d5e6f";

const rules1: RulesOfUse = {
  version: 1,
  createdAt: "2026-11-24T00:00:00Z",
  createdBy: null,
  maxBookingMinutes: 480,
  minBatteryPercent: 20,
  startWindowMinutes: 15,
  repositioningWindowMinutes: 10,
  overtimeGraceMinutes: 5,
  overtimeBlockMinutes: 15,
  overtimeRatePerBlock: "1.50",
  currency: "EUR",
  vehicleTypes: [
    { type: "E_BIKE", depositAmount: "20.00", currency: "EUR", batteryPercentPer30Minutes: 5 },
    { type: "E_SCOOTER", depositAmount: "30.00", currency: "EUR", batteryPercentPer30Minutes: 5 },
  ],
};

const rules2: RulesOfUse = { ...rules1, version: 2, createdBy: USER_ID, maxBookingMinutes: 240 };

const hub03: Hub = {
  id: "2b3c4d5e-0000-4000-8000-000000000103",
  code: "hub-03",
  name: "Canal Central",
  latitude: 40.6405,
  longitude: -8.6538,
  capacity: 10,
  vehiclesHeld: 3,
};

const day: PassType = {
  id: "7a1e0000-0000-4000-8000-000000000001",
  code: "DAY",
  name: "Day pass",
  price: "10.00",
  currency: "EUR",
  validityDays: 1,
  vehicleTypes: ["E_BIKE", "E_SCOOTER"],
};

const hotel: Hotel = {
  id: "5a7e0000-0000-4000-8000-000000000001",
  name: "Hotel Ria Azul",
  discountPercent: 10,
};

const demoCode: StayCode = {
  id: "57a90000-0000-4000-8000-000000000001",
  code: "RIA-DEMO-01",
  accommodationId: hotel.id,
  accommodationName: "Hotel Ria Azul",
  discountPercent: 10,
  checkIn: "2026-11-01",
  checkOut: "2027-02-28",
  redeemed: false,
};

function openAs(role: string) {
  saveSession({ userId: USER_ID, accessToken: TOKEN });
  vi.mocked(getProfile).mockResolvedValue({
    id: USER_ID,
    name: "Mara Silva",
    email: "mara.silva@example.com",
    role,
  });
  vi.mocked(getRules).mockResolvedValue(rules1);
  vi.mocked(listRulesVersions).mockResolvedValue([rules1]);
  vi.mocked(listHubs).mockResolvedValue([hub03]);
  vi.mocked(listPassTypes).mockResolvedValue([day]);
  vi.mocked(listHotels).mockResolvedValue([hotel]);
  vi.mocked(listStayCodes).mockResolvedValue([demoCode]);
  renderApp("/client");
}

const table = async (name: string) => within(await screen.findByRole("table", { name }));
const form = async (name: string) => within(await screen.findByRole("form", { name }));

beforeEach(() => {
  vi.resetAllMocks();
  sessionStorage.clear();
});

describe("client management page", () => {
  it("sends a visitor without a session to the login", () => {
    renderApp("/client");

    expect(screen.getByRole("heading", { name: "Log in" })).toBeInTheDocument();
  });

  it.each(["END_USER", "OPERATOR"])("tells a %s they have no access and loads nothing", async (role) => {
    openAs(role);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You do not have access to this page.",
    );
    expect(getRules).not.toHaveBeenCalled();
    expect(listStayCodes).not.toHaveBeenCalled();
  });

  it("is open to an admin too", async () => {
    openAs("ADMIN");

    expect(await screen.findByRole("form", { name: "Change the rules of use" })).toBeInTheDocument();
  });

  it("forgets an expired session and asks to log in again", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(getProfile).mockRejectedValue(new ApiError(401, {}));
    renderApp("/client");

    expect(await screen.findByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(loadSession()).toBeNull();
  });

  it("shows the rules, deposits, hubs, pass types, hotels and stay codes", async () => {
    openAs("CLIENT_MANAGER");

    const rules = await form("Change the rules of use");
    expect(rules.getByLabelText("Longest booking (minutes)")).toHaveValue("480");
    expect(rules.getByLabelText("Minimum battery (%)")).toHaveValue("20");
    expect(rules.getByLabelText("Overtime price per block (EUR)")).toHaveValue("1.50");
    expect(rules.getByLabelText("E-scooter battery per 30 minutes (%)")).toHaveValue("5");
    expect(screen.getByText(/^Version 1, .*\.$/, { selector: "p" })).toBeInTheDocument();
    expect(
      within(screen.getByRole("list", { name: "Versions of the rules" })).getByText(/Version 1,/),
    ).toBeInTheDocument();
    expect(getRules).toHaveBeenCalledWith(TOKEN);

    expect((await table("Deposits")).getByLabelText("Deposit of E-scooter")).toHaveValue("30.00");
    expect((await table("Hubs")).getByRole("row", { name: /hub-03/ })).toHaveTextContent("3 of 10");
    expect((await table("Pass types")).getByRole("row", { name: /DAY/ })).toHaveTextContent("Day pass");
    expect((await table("Hotels")).getByRole("row", { name: /Hotel Ria Azul/ })).toHaveTextContent("10%");
    expect((await table("Stay codes")).getByRole("row", { name: /RIA-DEMO-01/ })).toHaveTextContent(
      "Not used",
    );
  });

  // ------------------------------------------------------------- rules of use (BR21)

  it("saves the rules as a new version and shows it", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(changeRules).mockResolvedValue(rules2);
    vi.mocked(getRules).mockResolvedValue(rules2);
    vi.mocked(listRulesVersions).mockResolvedValue([rules2, rules1]);
    const user = userEvent.setup();
    const rules = await form("Change the rules of use");

    const longest = rules.getByLabelText("Longest booking (minutes)");
    await user.clear(longest);
    await user.type(longest, "240");
    await user.click(rules.getByRole("button", { name: "Save as a new version" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Saved as version 2.");
    expect(changeRules).toHaveBeenCalledWith(
      {
        maxBookingMinutes: 240,
        minBatteryPercent: 20,
        startWindowMinutes: 15,
        repositioningWindowMinutes: 10,
        overtimeGraceMinutes: 5,
        overtimeBlockMinutes: 15,
        overtimeRatePerBlock: "1.50",
        batteryPercentPer30Minutes: { E_BIKE: 5, E_SCOOTER: 5 },
      },
      TOKEN,
    );
    const versions = within(await screen.findByRole("list", { name: "Versions of the rules" }));
    expect(versions.getAllByRole("listitem")).toHaveLength(2);
    expect(versions.getAllByRole("listitem")[0]).toHaveTextContent("Version 2");
  });

  it("sends an empty field as missing, so the server names it", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(changeRules).mockRejectedValue(
      new ApiError(400, { maxBookingMinutes: "must not be null" }),
    );
    const user = userEvent.setup();
    const rules = await form("Change the rules of use");

    await user.clear(rules.getByLabelText("Longest booking (minutes)"));
    await user.click(rules.getByRole("button", { name: "Save as a new version" }));

    expect(vi.mocked(changeRules).mock.calls[0][0].maxBookingMinutes).toBeNull();
    expect(
      await rules.findByText("Enter a longest booking from 1 to 1440 minutes (24 hours)."),
    ).toBeInTheDocument();
  });

  it("shows the message of each field the server refused", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(changeRules).mockRejectedValue(
      new ApiError(400, {
        minBatteryPercent: "must be less than or equal to 100",
        overtimeRatePerBlock: "must be an amount",
        batteryPercentPer30Minutes: "must give 0 to 100",
      }),
    );
    const user = userEvent.setup();
    const rules = await form("Change the rules of use");

    await user.click(rules.getByRole("button", { name: "Save as a new version" }));

    expect(await rules.findByText("Enter a minimum battery from 0 to 100%.")).toBeInTheDocument();
    expect(
      rules.getByText("Enter an amount in euros with at most two decimals."),
    ).toBeInTheDocument();
    expect(rules.getByText("Enter a battery use from 0 to 100% for each vehicle type.")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("tells the manager when someone else changed the rules meanwhile", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(changeRules).mockRejectedValue(new ApiError(409, {}));
    const user = userEvent.setup();
    const rules = await form("Change the rules of use");

    await user.click(rules.getByRole("button", { name: "Save as a new version" }));

    expect(await rules.findByRole("alert")).toHaveTextContent(
      "The rules were changed by someone else. Reload the page and try again.",
    );
  });

  // ------------------------------------------------------------- deposits (BR4)

  it("saves the deposit of a vehicle type", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(changeDeposit).mockResolvedValue({
      type: "E_SCOOTER",
      depositAmount: "45.00",
      currency: "EUR",
      batteryPercentPer30Minutes: 5,
    });
    const user = userEvent.setup();
    const deposits = await table("Deposits");

    const input = deposits.getByLabelText("Deposit of E-scooter");
    await user.clear(input);
    await user.type(input, "45.00");
    await user.click(
      within(deposits.getByRole("row", { name: /E-scooter/ })).getByRole("button", { name: "Save" }),
    );

    expect(changeDeposit).toHaveBeenCalledWith("E_SCOOTER", "45.00", TOKEN);
    expect(getRules).toHaveBeenCalledTimes(2);
  });

  it("explains a deposit the server refused", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(changeDeposit).mockRejectedValue(new ApiError(400, { depositAmount: "bad" }));
    const user = userEvent.setup();
    const deposits = await table("Deposits");

    await user.click(
      within(deposits.getByRole("row", { name: /E-bike/ })).getByRole("button", { name: "Save" }),
    );

    expect(await deposits.findByRole("alert")).toHaveTextContent(
      "Enter an amount in euros with at most two decimals.",
    );
  });

  // ------------------------------------------------------------- hub capacity (BR14)

  it("changes the capacity of a hub, as the operator can", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(changeCapacity).mockResolvedValue({ ...hub03, capacity: 20 });
    const user = userEvent.setup();
    const hubs = await table("Hubs");

    const input = hubs.getByLabelText("Capacity of hub-03");
    await user.clear(input);
    await user.type(input, "20");
    await user.click(hubs.getByRole("button", { name: "Save" }));

    expect(changeCapacity).toHaveBeenCalledWith(hub03.id, 20, TOKEN);
  });

  // ------------------------------------------------------------- pass types (BR8)

  it("creates a pass type for the chosen vehicle types", async () => {
    openAs("CLIENT_MANAGER");
    const week: PassType = {
      ...day,
      id: "new-id",
      code: "WEEK",
      name: "Week pass",
      price: "40.00",
      validityDays: 7,
      vehicleTypes: ["E_BIKE"],
    };
    vi.mocked(createPassType).mockResolvedValue(week);
    const user = userEvent.setup();
    const create = await form("New pass type");

    await user.type(create.getByLabelText("Code"), "WEEK");
    await user.type(create.getByLabelText("Name"), "Week pass");
    await user.type(create.getByLabelText("Valid for (days)"), "7");
    await user.type(create.getByLabelText("Price (EUR)"), "40.00");
    await user.click(create.getByLabelText("E-scooter"));
    await user.click(create.getByRole("button", { name: "Create pass type" }));

    expect(createPassType).toHaveBeenCalledWith(
      { code: "WEEK", name: "Week pass", validityDays: 7, price: "40.00", vehicleTypes: ["E_BIKE"] },
      TOKEN,
    );
    expect(listPassTypes).toHaveBeenCalledTimes(2);
  });

  it("says when a pass type code is already in use", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(createPassType).mockRejectedValue(new ApiError(409, { code: "is already in use" }));
    const user = userEvent.setup();
    const create = await form("New pass type");

    await user.type(create.getByLabelText("Code"), "DAY");
    await user.click(create.getByRole("button", { name: "Create pass type" }));

    expect(await create.findByText("This code is already in use.")).toBeInTheDocument();
  });

  it("shows the fields the server refused when creating a pass type", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(createPassType).mockRejectedValue(
      new ApiError(400, {
        code: "x",
        name: "x",
        validityDays: "x",
        price: "x",
        vehicleTypes: "x",
      }),
    );
    const user = userEvent.setup();
    const create = await form("New pass type");

    await user.click(create.getByRole("button", { name: "Create pass type" }));

    expect(
      await create.findByText("Use 1 to 32 capital letters, digits or _, starting with a letter."),
    ).toBeInTheDocument();
    expect(create.getByText("Enter the name of the pass type.")).toBeInTheDocument();
    expect(create.getByText("Enter a validity from 1 to 365 days.")).toBeInTheDocument();
    expect(
      create.getByText("Enter an amount in euros with at most two decimals."),
    ).toBeInTheDocument();
    expect(create.getByText("Choose at least one vehicle type.")).toBeInTheDocument();
  });

  it("changes the price and the days of a pass type", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(updatePassType).mockResolvedValue({ ...day, price: "12.50", validityDays: 2 });
    const user = userEvent.setup();
    const types = await table("Pass types");

    const price = types.getByLabelText("Price of DAY");
    await user.clear(price);
    await user.type(price, "12.50");
    const days = types.getByLabelText("Days of DAY");
    await user.clear(days);
    await user.type(days, "2");
    await user.click(
      within(types.getByRole("row", { name: /DAY/ })).getByRole("button", { name: "Save" }),
    );

    expect(updatePassType).toHaveBeenCalledWith(day.id, { price: "12.50", validityDays: 2 }, TOKEN);
  });

  it("sends only what changed when a pass type is saved", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(updatePassType).mockResolvedValue(day);
    const user = userEvent.setup();
    const types = await table("Pass types");

    await user.click(
      within(types.getByRole("row", { name: /DAY/ })).getByRole("button", { name: "Save" }),
    );

    expect(updatePassType).toHaveBeenCalledWith(day.id, {}, TOKEN);
  });

  it("explains a pass type change the server refused", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(updatePassType).mockRejectedValue(new ApiError(400, { price: "bad" }));
    const user = userEvent.setup();
    const types = await table("Pass types");

    await user.click(
      within(types.getByRole("row", { name: /DAY/ })).getByRole("button", { name: "Save" }),
    );

    expect(await types.findByRole("alert")).toHaveTextContent(
      "Enter an amount in euros with at most two decimals.",
    );
  });

  // ------------------------------------------------------------- hotels and stay codes (BR9)

  it("registers a partner hotel with its discount", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(registerHotel).mockResolvedValue({ id: "h2", name: "Hotel Laguna", discountPercent: 15 });
    const user = userEvent.setup();
    const create = await form("New hotel");

    await user.type(create.getByLabelText("Hotel name"), "Hotel Laguna");
    await user.type(create.getByLabelText("Discount (%)"), "15");
    await user.click(create.getByRole("button", { name: "Register hotel" }));

    expect(registerHotel).toHaveBeenCalledWith({ name: "Hotel Laguna", discountPercent: 15 }, TOKEN);
    expect(listHotels).toHaveBeenCalledTimes(2);
  });

  it("says when a hotel name is already in use or the discount is wrong", async () => {
    openAs("CLIENT_MANAGER");
    const user = userEvent.setup();
    const create = await form("New hotel");

    vi.mocked(registerHotel).mockRejectedValueOnce(new ApiError(409, { name: "is already in use" }));
    await user.type(create.getByLabelText("Hotel name"), "Hotel Ria Azul");
    await user.click(create.getByRole("button", { name: "Register hotel" }));
    expect(await create.findByText("This name is already in use.")).toBeInTheDocument();

    vi.mocked(registerHotel).mockRejectedValueOnce(
      new ApiError(400, { discountPercent: "must be at most 100" }),
    );
    await user.click(create.getByRole("button", { name: "Register hotel" }));
    expect(await create.findByText("Enter a discount from 0 to 100%.")).toBeInTheDocument();
  });

  it("issues a stay code for a hotel and shows it", async () => {
    openAs("CLIENT_MANAGER");
    const issued: StayCode = { ...demoCode, id: "s2", code: "RIA-7K3M9Q", checkIn: "2026-12-01", checkOut: "2026-12-05" };
    vi.mocked(issueStayCode).mockResolvedValue(issued);
    const user = userEvent.setup();
    const issue = await form("New stay code");

    await user.selectOptions(issue.getByLabelText("Hotel"), hotel.id);
    await user.type(issue.getByLabelText("Check-in"), "2026-12-01");
    await user.type(issue.getByLabelText("Check-out"), "2026-12-05");
    await user.click(issue.getByRole("button", { name: "Issue code" }));

    expect(issueStayCode).toHaveBeenCalledWith(
      { accommodationId: hotel.id, checkIn: "2026-12-01", checkOut: "2026-12-05" },
      TOKEN,
    );
    expect(await screen.findByRole("status")).toHaveTextContent("Code issued: RIA-7K3M9Q");
    expect(listStayCodes).toHaveBeenCalledTimes(2);
  });

  it("explains a stay code the server refused", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(issueStayCode).mockRejectedValue(
      new ApiError(400, { checkOut: "must not be before the check-in", checkIn: "x", accommodationId: "x" }),
    );
    const user = userEvent.setup();
    const issue = await form("New stay code");

    await user.click(issue.getByRole("button", { name: "Issue code" }));

    expect(await issue.findByText("Check-out cannot be before check-in.")).toBeInTheDocument();
    expect(issue.getByText("Choose the first day of the stay.")).toBeInTheDocument();
    expect(issue.getByText("Choose a partner hotel.")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("shows which stay codes were used", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(listStayCodes).mockResolvedValue([{ ...demoCode, redeemed: true }]);
    renderApp("/client");

    expect(
      (await table("Stay codes")).getByRole("row", { name: /RIA-DEMO-01/ }),
    ).toHaveTextContent("Used");
  });

  // ------------------------------------------------------------- failures

  it("shows an error when the page cannot be loaded", async () => {
    openAs("CLIENT_MANAGER");
    vi.mocked(getRules).mockRejectedValue(new ApiError(500, {}));
    renderApp("/client");

    expect((await screen.findAllByRole("alert"))[0]).toHaveTextContent(
      "Something went wrong. Please try again.",
    );
  });
});
