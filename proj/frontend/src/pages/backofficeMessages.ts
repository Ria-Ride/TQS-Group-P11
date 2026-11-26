/** What the client manager reads when the server refuses a value (US14), field by field. */
export const MONEY_MESSAGE = "Enter an amount in euros with at most two decimals.";

export const RULES_MESSAGES: Record<string, string> = {
  maxBookingMinutes: "Enter a longest booking from 1 to 1440 minutes (24 hours).",
  minBatteryPercent: "Enter a minimum battery from 0 to 100%.",
  startWindowMinutes: "Enter a start window from 1 to 1440 minutes.",
  repositioningWindowMinutes: "Enter a repositioning window from 1 to 1440 minutes.",
  overtimeGraceMinutes: "Enter an overtime grace from 0 to 1440 minutes.",
  overtimeBlockMinutes: "Enter an overtime block from 1 to 1440 minutes.",
  overtimeRatePerBlock: MONEY_MESSAGE,
  batteryPercentPer30Minutes: "Enter a battery use from 0 to 100% for each vehicle type.",
};

export const RULES_CHANGED_BY_SOMEONE_ELSE =
  "The rules were changed by someone else. Reload the page and try again.";

export const PASS_TYPE_MESSAGES: Record<string, string> = {
  code: "Use 1 to 32 capital letters, digits or _, starting with a letter.",
  name: "Enter the name of the pass type.",
  validityDays: "Enter a validity from 1 to 365 days.",
  price: MONEY_MESSAGE,
  vehicleTypes: "Choose at least one vehicle type.",
};

export const PASS_TYPE_CODE_IN_USE = "This code is already in use.";

export const HOTEL_MESSAGES: Record<string, string> = {
  name: "Enter the name of the hotel.",
  discountPercent: "Enter a discount from 0 to 100%.",
};

export const HOTEL_NAME_IN_USE = "This name is already in use.";

export const STAY_CODE_MESSAGES: Record<string, string> = {
  accommodationId: "Choose a partner hotel.",
  checkIn: "Choose the first day of the stay.",
  checkOut: "Choose the last day of the stay.",
};

export const CHECK_OUT_BEFORE_CHECK_IN = "Check-out cannot be before check-in.";
