import type { Hub, HubVehicle, VehicleAction, VehicleState, VehicleType } from "../api/fleet";

export const STATE_LABELS: Record<VehicleState, string> = {
  DEACTIVATED: "Deactivated",
  AVAILABLE: "Available",
  RESERVED: "Reserved",
  IN_USE: "In use",
  MAINTENANCE: "Maintenance",
  IN_TRANSIT: "In transit",
  RETIRED: "Retired",
};

export const TYPE_LABELS: Record<VehicleType, string> = {
  E_BIKE: "E-bike",
  E_SCOOTER: "E-scooter",
};

export const VEHICLE_TYPES: VehicleType[] = ["E_BIKE", "E_SCOOTER"];

export const ACTION_LABELS: Record<VehicleAction, string> = {
  ACTIVATE: "Activate",
  DEACTIVATE: "Deactivate",
  START_MOVE: "Start move",
  CONFIRM_ARRIVAL: "Confirm arrival",
  RETIRE: "Retire",
};

/**
 * The operator actions of the vehicle lifecycle (BR16, Product Specs 3.3), so the page only offers
 * buttons that can work. The server stays the authority: it refuses anything else with 409.
 */
export const ACTIONS_BY_STATE: Record<VehicleState, VehicleAction[]> = {
  DEACTIVATED: ["ACTIVATE", "RETIRE"],
  AVAILABLE: ["DEACTIVATE", "START_MOVE", "RETIRE"],
  RESERVED: [],
  IN_USE: [],
  MAINTENANCE: ["RETIRE"],
  IN_TRANSIT: ["CONFIRM_ARRIVAL"],
  RETIRED: [],
};

/** "hub-05 · Hotel Ria Azul", for hub choices. */
export function hubLabel(hub: Hub): string {
  return `${hub.code} · ${hub.name}`;
}

/** An empty or non-numeric field is sent as null, so the server reports it as missing. */
export function parseNumber(text: string): number | null {
  if (text.trim() === "") {
    return null;
  }
  const value = Number(text);
  return Number.isNaN(value) ? null : value;
}

export const CODE_MESSAGE = "Use 1 to 64 letters, digits, - or _.";
export const CODE_IN_USE = "This code is already in use.";
export const HUB_FULL = "This hub is full.";
export const CAPACITY_MESSAGE = "Enter a capacity of at least 1.";
export const CAPACITY_BELOW_HELD = "The hub holds more vehicles than that.";
export const TRANSITION_REFUSED = "This change is not allowed in the vehicle's current state.";

/** The lengths of ride a tourist can pick; the API takes any number of minutes from 1 to 1440. */
export const RIDE_LENGTHS: { minutes: number; label: string }[] = [
  { minutes: 30, label: "Up to 30 minutes" },
  { minutes: 60, label: "Up to 1 hour" },
  { minutes: 120, label: "Up to 2 hours" },
  { minutes: 180, label: "Up to 3 hours" },
  { minutes: 240, label: "Up to 4 hours" },
  { minutes: 480, label: "Up to 8 hours" },
];

/** Above this, no battery can do the ride (the API does not cap the percentage at 100). */
const FULL_BATTERY = 100;

/** What a tourist reads next to a vehicle that cannot be booked (BR1, BR2). */
export function unbookableText(vehicle: HubVehicle): string {
  switch (vehicle.unbookableReason) {
    case "NOT_AVAILABLE":
      return `Not available (${STATE_LABELS[vehicle.state]})`;
    case "NO_BATTERY_READING":
      return "Battery level unknown";
    case "LOW_BATTERY":
      return `Battery too low (needs at least ${vehicle.requiredBatteryPercent}%)`;
    case "INSUFFICIENT_FOR_DURATION":
      return vehicle.requiredBatteryPercent > FULL_BATTERY
        ? "This ride is too long for one battery charge"
        : `Not enough battery for this ride (needs ${vehicle.requiredBatteryPercent}%)`;
    case null:
      return "Bookable";
  }
}
