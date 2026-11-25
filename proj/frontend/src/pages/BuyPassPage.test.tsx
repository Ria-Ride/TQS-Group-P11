import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";
import { buyPass, listPassTypes, quotePrice, type Pass, type PassType } from "../api/passes";
import { loadSession, saveSession } from "../auth/session";
import { renderApp } from "../test/renderApp";

vi.mock("../api/passes");

const TOKEN = "token-for-ana";
const USER_ID = "4b1d6f2e-8a3c-4e5b-9d7f-1a2b3c4d5e6f";

const day: PassType = {
  id: "7a1e0000-0000-4000-8000-000000000001",
  code: "DAY",
  name: "Day pass",
  price: "10.00",
  currency: "EUR",
  validityDays: 1,
  vehicleTypes: ["E_BIKE", "E_SCOOTER"],
};

const multiDay: PassType = {
  ...day,
  id: "7a1e0000-0000-4000-8000-000000000003",
  code: "MULTI_DAY",
  name: "Multi-day pass",
  price: "25.00",
  validityDays: 3,
};

const issued: Pass = {
  id: "9d000000-0000-4000-8000-000000000001",
  passTypeId: day.id,
  passTypeCode: "DAY",
  price: "9.00",
  currency: "EUR",
  validFrom: "2026-11-24T10:15:30Z",
  validTo: "2026-11-25T10:15:30Z",
};

function openPage() {
  saveSession({ userId: USER_ID, accessToken: TOKEN });
  vi.mocked(listPassTypes).mockResolvedValue([day, multiDay]);
  renderApp("/passes/buy");
}

const payButton = () => screen.findByRole("button", { name: /^Pay / });

async function applyCode(code: string) {
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText("Stay code (optional)"), code);
  await user.click(screen.getByRole("button", { name: "Apply code" }));
}

beforeEach(() => {
  vi.mocked(listPassTypes).mockReset();
  vi.mocked(quotePrice).mockReset();
  vi.mocked(buyPass).mockReset();
  sessionStorage.clear();
});

describe("buy a pass page", () => {
  it("sends a visitor without a session to the login", () => {
    renderApp("/passes/buy");

    expect(screen.getByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(listPassTypes).not.toHaveBeenCalled();
  });

  it("forgets an expired session and asks to log in again", async () => {
    saveSession({ userId: USER_ID, accessToken: TOKEN });
    vi.mocked(listPassTypes).mockRejectedValue(new ApiError(401, {}));
    renderApp("/passes/buy");

    expect(await screen.findByRole("heading", { name: "Log in" })).toBeInTheDocument();
    expect(loadSession()).toBeNull();
  });

  it("shows each pass type with its price and validity, the first one chosen", async () => {
    openPage();

    const types = await screen.findByRole("radiogroup", { name: "Pass" });
    expect(within(types).getByRole("radio", { name: /Day pass.*10\.00 EUR.*1 day/ })).toBeChecked();
    expect(within(types).getByRole("radio", { name: /Multi-day pass.*25\.00 EUR.*3 days/ })).not.toBeChecked();
    expect(await payButton()).toHaveTextContent("Pay 10.00 EUR");
  });

  it("follows the chosen pass type in the amount to pay", async () => {
    openPage();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("radio", { name: /Multi-day pass/ }));

    expect(await payButton()).toHaveTextContent("Pay 25.00 EUR");
  });

  it("says the payment is simulated and approves by default", async () => {
    openPage();

    const cards = await screen.findByRole("radiogroup", { name: /Test card/ });
    expect(within(cards).getByRole("radio", { name: /approved/i })).toBeChecked();
    expect(screen.getByText(/simulated payment/i)).toBeInTheDocument();
  });

  // ------------------------------------------------------------- stay code (BR9)

  it("shows the discounted price when the stay code is valid", async () => {
    openPage();
    vi.mocked(quotePrice).mockResolvedValue({
      passTypeId: day.id,
      basePrice: "10.00",
      price: "9.00",
      currency: "EUR",
      discountPercent: 10,
    });

    await applyCode("ria-demo-01");

    expect(quotePrice).toHaveBeenCalledWith(day.id, "ria-demo-01", TOKEN);
    expect(await screen.findByText(/10% off/)).toBeInTheDocument();
    expect(await payButton()).toHaveTextContent("Pay 9.00 EUR");
  });

  it("asks the discounted price again when the pass type changes", async () => {
    openPage();
    vi.mocked(quotePrice).mockResolvedValueOnce({
      passTypeId: day.id,
      basePrice: "10.00",
      price: "9.00",
      currency: "EUR",
      discountPercent: 10,
    });
    vi.mocked(quotePrice).mockResolvedValueOnce({
      passTypeId: multiDay.id,
      basePrice: "25.00",
      price: "22.50",
      currency: "EUR",
      discountPercent: 10,
    });
    await applyCode("RIA-DEMO-01");
    await screen.findByText(/10% off/);

    await userEvent.setup().click(screen.getByRole("radio", { name: /Multi-day pass/ }));

    expect(quotePrice).toHaveBeenLastCalledWith(multiDay.id, "RIA-DEMO-01", TOKEN);
    expect(await screen.findByRole("button", { name: "Pay 22.50 EUR" })).toBeInTheDocument();
  });

  it.each([
    ["is unknown", /do not know this stay code/i],
    ["is not valid today", /not valid today/i],
    ["is linked to another account", /already used by another account/i],
  ])("explains a refused code (%s) and offers the base price", async (reason, message) => {
    openPage();
    vi.mocked(quotePrice).mockRejectedValue(new ApiError(400, { stayCode: reason }));

    await applyCode("NOPE-0000");

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(await payButton()).toHaveTextContent("Pay 10.00 EUR");
    expect(screen.getByRole("button", { name: /^Pay / })).toBeDisabled();
  });

  it("lets the tourist continue at the base price after a refused code", async () => {
    openPage();
    vi.mocked(quotePrice).mockRejectedValue(new ApiError(400, { stayCode: "is unknown" }));
    vi.mocked(buyPass).mockResolvedValue({ ...issued, price: "10.00" });
    const user = userEvent.setup();
    await applyCode("NOPE-0000");

    await user.click(await screen.findByRole("button", { name: "Continue without the code" }));
    expect(screen.getByLabelText("Stay code (optional)")).toHaveValue("");
    await user.click(await screen.findByRole("button", { name: "Pay 10.00 EUR" }));

    expect(buyPass).toHaveBeenCalledWith(
      { passTypeId: day.id, stayCode: null, paymentToken: "tok_approve" },
      TOKEN,
    );
  });

  // ------------------------------------------------------------- paying (BR8, BR10)

  it("shows the issued pass with what was paid and its validity", async () => {
    openPage();
    vi.mocked(buyPass).mockResolvedValue(issued);
    const user = userEvent.setup();

    await user.click(await payButton());

    const result = await screen.findByRole("region", { name: "Your pass" });
    expect(within(result).getByText(/Day pass/)).toBeInTheDocument();
    expect(within(result).getByText(/9\.00 EUR/)).toBeInTheDocument();
    expect(within(result).getByText(/Valid from/)).toBeInTheDocument();
    expect(within(result).getByText(/Valid until/)).toBeInTheDocument();
  });

  it("sends the chosen test card", async () => {
    openPage();
    vi.mocked(buyPass).mockRejectedValue(new ApiError(402, { payment: "was declined" }));
    const user = userEvent.setup();

    await user.click(await screen.findByRole("radio", { name: /declined/i }));
    await user.click(await payButton());

    expect(buyPass).toHaveBeenCalledWith(
      { passTypeId: day.id, stayCode: null, paymentToken: "tok_decline" },
      TOKEN,
    );
  });

  it.each([
    [402, /payment was declined/i],
    [504, /did not answer in time/i],
  ])("explains a payment that was not approved (%i) and issues no pass", async (status, message) => {
    openPage();
    vi.mocked(buyPass).mockRejectedValue(new ApiError(status, { payment: "x" }));
    const user = userEvent.setup();

    await user.click(await payButton());

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.getByRole("alert")).toHaveTextContent(/no pass was issued/i);
    expect(screen.queryByRole("region", { name: "Your pass" })).not.toBeInTheDocument();
  });

  it("shows a code refused at payment time and lets the tourist continue without it", async () => {
    openPage();
    vi.mocked(quotePrice).mockResolvedValue({
      passTypeId: day.id,
      basePrice: "10.00",
      price: "9.00",
      currency: "EUR",
      discountPercent: 10,
    });
    vi.mocked(buyPass).mockRejectedValue(
      new ApiError(400, { stayCode: "is linked to another account" }),
    );
    const user = userEvent.setup();
    await applyCode("RIA-DEMO-01");

    await user.click(await screen.findByRole("button", { name: "Pay 9.00 EUR" }));

    expect(await screen.findByText(/already used by another account/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continue without the code" })).toBeInTheDocument();
  });
});
