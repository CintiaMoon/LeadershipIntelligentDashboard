import { request as playwrightRequest, type APIRequestContext, type APIResponse } from '@playwright/test';
import { companyOf, emailOf, PASSWORD } from './accounts.ts';

const API = process.env.E2E_API_URL ?? 'http://localhost:8080';

/**
 * Talks to the real backend as one of the demo accounts, for checks that go underneath the user interface: it signs in with the
 * password like a browser would, keeps the session cookie in a jar of its own and sends the CSRF token and the company header on
 * every call. The first argument stays for the callers' signature; each persona has its own jar so several can act in one test.
 */
export async function asPersona(_request: APIRequestContext, persona: string) {
  const jar = await playwrightRequest.newContext();
  // sign-in is a state-changing call, so it carries a CSRF token too; the session it starts gets a new one
  const first = (await (await jar.get(`${API}/api/v1/auth/csrf`)).json()) as { token: string };
  const login = await jar.post(`${API}/api/v1/auth/login`, { data: { email: emailOf(persona), password: PASSWORD }, headers: { 'X-XSRF-TOKEN': first.token } });
  if (!login.ok()) throw new Error(`sign-in of ${persona} failed: ${login.status()} ${await login.text()}`);
  const csrf = (await (await jar.get(`${API}/api/v1/auth/csrf`)).json()) as { token: string };
  const company = companyOf(persona);
  const headers: Record<string, string> = { 'X-XSRF-TOKEN': csrf.token };
  if (company) headers['X-Company-Id'] = company;
  const url = (path: string) => `${API}/api/v1${path}`;
  return {
    get: (path: string) => jar.get(url(path), { headers }),
    post: (path: string, data?: unknown) => jar.post(url(path), { headers, data }),
    put: (path: string, data?: unknown) => jar.put(url(path), { headers, data }),
    json: async <T = any>(path: string): Promise<T> => {
      const response: APIResponse = await jar.get(url(path), { headers });
      if (!response.ok()) throw new Error(`GET ${path} failed: ${response.status()}`);
      return (await response.json()) as T;
    },
  };
}

export type Persona = Awaited<ReturnType<typeof asPersona>>;

export async function runsOf(persona: Persona, organizationId: string) {
  const page = await persona.json<{ items: any[] }>(`/scoring-runs?organizationId=${organizationId}&limit=50`);
  // newest first from the API; the tests think oldest first
  return [...page.items].reverse();
}
