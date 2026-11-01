import { ApiError } from './errors';

const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? '';

let getToken: () => string | null = () => null;
let onUnauthorized: () => void = () => undefined;

/** Wired once by the auth provider: where the bearer token comes from and what to do on a 401. */
export function configureApi(options: { getToken: () => string | null; onUnauthorized: () => void }): void {
  getToken = options.getToken;
  onUnauthorized = options.onUnauthorized;
}

export type Query = Record<string, string | number | boolean | null | undefined>;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT';
  query?: Query;
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
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

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
  const token = getToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';

  const response = await fetch(`${BASE}/api/v1${path}${queryString(options.query)}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  });

  if (!response.ok) {
    const body: unknown = await response.json().catch(() => null);
    const error = ApiError.fromBody(response.status, body as never, response.headers.get('X-Correlation-Id'));
    if (response.status === 401) onUnauthorized();
    throw error;
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}
