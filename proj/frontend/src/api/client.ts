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
  method?: "GET" | "POST" | "PATCH";
  body?: unknown;
  token?: string;
}

export async function requestJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (options.token) {
    headers["Authorization"] = `Bearer ${options.token}`;
  }

  const response = await fetch(`/api${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (!response.ok) {
    throw new ApiError(response.status, await readFieldErrors(response));
  }
  return (await response.json()) as T;
}

/** The backend sends problem details with an "errors" map; anything else means no field errors. */
async function readFieldErrors(response: Response): Promise<FieldErrors> {
  try {
    const body = (await response.json()) as { errors?: FieldErrors };
    return body.errors ?? {};
  } catch {
    return {};
  }
}