export type FieldErrors = Record<string, string>;

/**
 * An HTTP error from the API, with the backend's field → message map (may be empty) and the rest
 * of the problem body, for errors that carry more (US06: a full hub lists nearby hubs).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: FieldErrors;
  readonly body: Record<string, unknown>;

  constructor(status: number, fieldErrors: FieldErrors = {}, body: Record<string, unknown> = {}) {
    super(`Request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.body = body;
  }
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH";
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
    const problem = await readProblem(response);
    throw new ApiError(response.status, problem.errors ?? {}, problem);
  }
  return (await response.json()) as T;
}

/** The backend sends problem details with an "errors" map; anything else means no field errors. */
async function readProblem(
  response: Response,
): Promise<Record<string, unknown> & { errors?: FieldErrors }> {
  try {
    const body: unknown = await response.json();
    return typeof body === "object" && body !== null ? (body as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}