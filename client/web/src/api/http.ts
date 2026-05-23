import { apiErrorMessage, type ApiErrorPayload } from "./errorMessages";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

export type RequestOptions = {
  token?: string | null;
  officeId?: number | null;
  method?: string;
  body?: unknown;
  query?: Record<string, string | number | undefined | null>;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string | null;
  readonly payload?: ApiErrorPayload | null;

  constructor(status: number, message: string, code?: string | null, payload?: ApiErrorPayload | null) {
    super(message);
    this.status = status;
    this.code = code;
    this.payload = payload;
  }
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(`${API_BASE}${path}`, window.location.origin);
  Object.entries(options.query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      url.searchParams.set(key, String(value));
    }
  });

  const headers = new Headers();
  headers.set("Accept", "application/json");
  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  if (options.token) {
    headers.set("Authorization", `Bearer ${options.token}`);
  }
  if (options.officeId) {
    headers.set("X-Office-Id", String(options.officeId));
  }

  const response = await fetch(url, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });

  if (!response.ok) {
    let message = apiErrorMessage(response.status);
    let payload: ApiErrorPayload | null = null;
    const contentType = response.headers.get("Content-Type") ?? "";
    try {
      if (contentType.includes("application/json")) {
        payload = (await response.json()) as ApiErrorPayload;
        message = apiErrorMessage(response.status, payload);
      } else {
        message = apiErrorMessage(response.status, null, await response.text());
      }
    } catch {
      // Keep the generic message when the server does not return a readable error body.
    }
    throw new ApiError(response.status, message, payload?.code, payload);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
