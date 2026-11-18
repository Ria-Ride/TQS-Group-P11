import type { FieldErrors } from "../api/client";

export const GENERIC_ERROR = "Something went wrong. Please try again.";

/** Replaces the backend's technical messages with ones written for tourists, field by field. */
export function friendlyFieldErrors(
  fieldErrors: FieldErrors,
  messages: Record<string, string>,
): FieldErrors {
  const friendly: FieldErrors = {};
  for (const field of Object.keys(fieldErrors)) {
    friendly[field] = messages[field] ?? fieldErrors[field];
  }
  return friendly;
}