import { ApiError } from './errors';

const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? '';

interface ApiConfig {
  /** The bearer token of the external identity provider, if that is how the user signs in. */
  getToken: () => string | null;
  /** Called when an authenticated call is refused with 401: the session is over. */
  onUnauthorized: () => void;
  /** The company named in the address bar; sent as {@code X-Company-Id} (decision D69). */
  getCompany?: () => string | null;
  /** Send the CSRF token the server issued on every state-changing call that has no bearer token (decision D68). */
  csrf?: boolean;
}

let config: ApiConfig = { getToken: () => null, onUnauthorized: () => undefined };

/** Wired once by the auth provider: where the credential comes from and what to do on a 401. */
export function configureApi(options: ApiConfig): void {
  config = options;
  csrfFallback = null;
}

export type Query = Record<string, string | number | boolean | null | undefined>;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  query?: Query;
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  /** A call made before there is a session (sign-in, registration, reset): a 401 is its answer, not the end of a session. */
  anonymous?: boolean;
  /** Leave the company header out (the account and company-list calls belong to no company). */
  noCompany?: boolean;
}

function queryString(query?: Query): string {
  if (!query) return '';
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
  }
  const text = params.toString();
  return text ? `?${text}` : '';
}

const SAFE = new Set(['GET', 'HEAD', 'OPTIONS']);
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

/** The token from the last answer, for the case where the cookie cannot be read (it is the cookie that the server keeps current). */
let csrfFallback: string | null = null;

function readCookie(name: string): string | null {
  for (const part of document.cookie.split(';')) {
    const [key, ...rest] = part.trim().split('=');
    if (key === name) return decodeURIComponent(rest.join('='));
  }
  return null;
}

/** The CSRF token for the next state-changing call: the cookie the server set, or a new one fetched from it. */
async function csrfToken(refresh: boolean): Promise<string> {
  if (!refresh) {
    const known = readCookie(CSRF_COOKIE) ?? csrfFallback;
    if (known) return known;
  }
  const response = await fetch(`${BASE}/api/v1/auth/csrf`, { headers: { Accept: 'application/json' }, credentials: 'same-origin' });
  const body = (await response.json().catch(() => null)) as { token?: string } | null;
  csrfFallback = body?.token ?? null;
  return readCookie(CSRF_COOKIE) ?? csrfFallback ?? '';
}

/** A new session issues a new CSRF token; forget the one we knew. */
export function forgetCsrfToken(): void {
  csrfFallback = null;
}

async function buildHeaders(method: string, accept: string, options: RequestOptions, refreshCsrf: boolean): Promise<Record<string, string>> {
  const headers: Record<string, string> = { Accept: accept, ...options.headers };
  const token = config.getToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (!options.noCompany) {
    const company = config.getCompany?.();
    if (company) headers['X-Company-Id'] = company;
  }
  if (config.csrf && !token && !SAFE.has(method)) headers[CSRF_HEADER] = await csrfToken(refreshCsrf);
  return headers;
}

function failure(response: Response, body: unknown): ApiError {
  const retry = Number.parseInt(response.headers.get('Retry-After') ?? '', 10);
  return ApiError.fromBody(response.status, body as never, response.headers.get('X-Correlation-Id'), Number.isFinite(retry) ? retry : undefined);
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const url = `${BASE}/api/v1${path}${queryString(options.query)}`;

  const send = async (refreshCsrf: boolean): Promise<Response> => {
    const headers = await buildHeaders(method, 'application/json', options, refreshCsrf);
    if (options.body !== undefined) headers['Content-Type'] = 'application/json';
    return fetch(url, {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
      credentials: 'same-origin',
    });
  };

  let response = await send(false);
  // a CSRF token that went stale (a new session, a cleared cookie) is replaced once and the call repeated
  if (response.status === 403 && config.csrf && !SAFE.has(method)) {
    const refused: unknown = await response.clone().json().catch(() => null);
    if ((refused as { code?: string } | null)?.code === 'CSRF_TOKEN_INVALID') {
      response = await send(true);
    }
  }

  if (!response.ok) {
    const body: unknown = await response.json().catch(() => null);
    const error = failure(response, body);
    if (response.status === 401 && !options.anonymous) config.onUnauthorized();
    throw error;
  }
  // register, resend and forgot-password answer 202 with no body, and a delete 204: an empty answer is not an error
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export interface Download {
  blob: Blob;
  filename: string;
}

/** A file the backend generates (a report as CSV or PDF): the bytes with the name the server gave them. */
export async function apiDownload(path: string, query: Query, accept: string, signal?: AbortSignal): Promise<Download> {
  const headers = await buildHeaders('GET', accept, {}, false);
  const response = await fetch(`${BASE}/api/v1${path}${queryString(query)}`, { headers, signal, credentials: 'same-origin' });
  if (!response.ok) {
    const body: unknown = await response.json().catch(() => null);
    const error = failure(response, body);
    if (response.status === 401) config.onUnauthorized();
    throw error;
  }
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const filename = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? 'report';
  return { blob: await response.blob(), filename };
}
