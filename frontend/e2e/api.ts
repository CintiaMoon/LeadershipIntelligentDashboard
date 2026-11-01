import type { APIRequestContext } from '@playwright/test';
import { tokenFor } from './seed';

const API = process.env.E2E_API_URL ?? 'http://localhost:8080';

/** Talks to the real backend as a persona, for checks that go underneath the user interface. */
export async function asPersona(request: APIRequestContext, persona: string) {
  const token = await tokenFor(persona);
  const headers = { Authorization: `Bearer ${token}` };
  return {
    get: (path: string) => request.get(`${API}/api/v1${path}`, { headers }),
    post: (path: string, data?: unknown) => request.post(`${API}/api/v1${path}`, { headers, data }),
    json: async <T = any>(path: string): Promise<T> => {
      const response = await request.get(`${API}/api/v1${path}`, { headers });
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
