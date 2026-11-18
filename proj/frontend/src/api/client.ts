export type FieldErrors = Record<string, string>;

/** An HTTP error from the API, with the backend's field → message map (may be empty). */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: FieldErrors;

  constructor(status: number, fieldErrors: FieldErrors = {}) {
    super(`Request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

export interface RequestOptions {
  method?: "GET" | "POST";
  body?: unknown;
  token?: string;
}

export async function requestJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
  throw new Error("not implemented yet");
}