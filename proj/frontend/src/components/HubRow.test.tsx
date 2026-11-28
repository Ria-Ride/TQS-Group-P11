import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { Hub } from "../api/fleet";
import { HubRow } from "./HubRow";

vi.mock("../api/fleet");

const hub03: Hub = {
  id: "2b3c4d5e-0000-4000-8000-000000000103",
  code: "hub-03",
  name: "Canal Central",
  latitude: 40.6405,
  longitude: -8.6538,
  capacity: 10,
  vehiclesHeld: 2,
};

function row(hub: Hub) {
  return (
    <table>
      <tbody>
        <HubRow hub={hub} token="token" onChanged={() => {}} />
      </tbody>
    </table>
  );
}

describe("hub row", () => {
  it("shows the saved capacity", () => {
    render(row(hub03));

    expect(screen.getByLabelText("Capacity of hub-03")).toHaveValue("10");
  });

  /** The list was reloaded with a capacity changed elsewhere: the field follows it. */
  it("shows the new saved capacity when the hub is reloaded with another one", async () => {
    const user = userEvent.setup();
    const { rerender } = render(row(hub03));
    const input = screen.getByLabelText("Capacity of hub-03");
    await user.clear(input);
    await user.type(input, "15");

    rerender(row({ ...hub03, capacity: 12 }));

    expect(input).toHaveValue("12");
  });

  /** A reload that brings the same capacity keeps what the user is typing (no "1020"). */
  it("keeps what the user typed when the hub is reloaded with the same capacity", async () => {
    const user = userEvent.setup();
    const { rerender } = render(row(hub03));
    const input = screen.getByLabelText("Capacity of hub-03");
    await user.clear(input);
    await user.type(input, "20");

    rerender(row({ ...hub03 }));

    expect(input).toHaveValue("20");
  });
});
