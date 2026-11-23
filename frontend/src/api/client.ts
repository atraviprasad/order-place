import { getToken, keycloak } from '../auth/keycloak';

export interface FieldError {
  field: string;
  message: string;
}

/** Mirrors the backend ErrorResponse (code, detail, traceId, field errors). */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];
  readonly traceId: string | null;

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = [], traceId: string | null = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.traceId = traceId;
  }
}

type Query = Record<string, string | number | boolean | null | undefined>;

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  query?: Query;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

const BASE = '/api/v1';

function buildUrl(path: string, query?: Query): string {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(query ?? {})) {
    if (v !== undefined && v !== null && v !== '') params.set(k, String(v));
  }
  const qs = params.toString();
  return `${BASE}${path}${qs ? `?${qs}` : ''}`;
}

async function send(url: string, opts: RequestOptions, token: string): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json', Authorization: `Bearer ${token}`, ...opts.headers };
  // a file upload (FormData) is sent as it is: the browser sets the multipart Content-Type with its boundary itself
  const isForm = opts.body instanceof FormData;
  if (opts.body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
  return fetch(url, {
    method: opts.method ?? 'GET',
    headers,
    body: opts.body === undefined ? undefined : isForm ? (opts.body as FormData) : JSON.stringify(opts.body),
    signal: opts.signal,
  });
}

export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const url = buildUrl(path, opts.query);
  let res = await send(url, opts, await getToken());

  if (res.status === 401) {
    // the token may have been revoked or expired early: force one refresh and retry once
    try {
      await keycloak.updateToken(-1);
    } catch {
      await keycloak.login();
    }
    res = await send(url, opts, await getToken());
  }

  if (!res.ok) {
    let body: { code?: string; detail?: string; traceId?: string; errors?: FieldError[] } = {};
    try {
      body = await res.json();
    } catch {
      /* the gateway or a proxy may return an empty or non-JSON body */
    }
    throw new ApiError(res.status, body.code ?? `HTTP_${res.status}`, body.detail ?? defaultMessage(res.status), body.errors ?? [], body.traceId ?? null);
  }
  // 202 and 204 answers have no body (for example reset-password); parse only when there is something to read
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

function defaultMessage(status: number): string {
  if (status === 403) return 'You are not allowed to do this.';
  if (status === 404) return 'Not found.';
  if (status >= 500) return 'The server had a problem. Please try again.';
  return `Request failed (${status}).`;
}

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.fieldErrors.length ? `${e.message}: ${e.fieldErrors.map((f) => `${f.field} ${f.message}`).join(', ')}` : e.message;
  if (e instanceof TypeError) return 'Cannot reach the server. Check that the API gateway is running.';
  return e instanceof Error ? e.message : 'Something went wrong.';
}
