import { http, HttpResponse } from 'msw';
import { configureApi, forgetCsrfToken } from '@/services/api/client';
import type { Account, Company } from '@/types/identity';
import type { AuthContextValue } from '@/app/providers/auth-context';
import { principalFor } from '@/services/auth/principal';

export const API = 'http://localhost:3000/api/v1';

/** The CSRF endpoint every state-changing call asks for first. */
export const csrfHandler = http.get(`${API}/auth/csrf`, () => HttpResponse.json({ headerName: 'X-XSRF-TOKEN', token: 'test-token' }));

/** The client as the session provider configures it: no bearer token, the company of the address, CSRF on. */
export function configureSession(company: string | null = 'acme'): void {
  forgetCsrfToken();
  configureApi({ getToken: () => null, onUnauthorized: () => undefined, getCompany: () => company, csrf: true });
}

export const ana: Account = { id: 'u-1', email: 'ana@example.com', displayName: 'Ana Silva', platformAdmin: false, companies: [{ id: 'acme', name: 'Acme Corp' }] };

export const acme = (roles: string[] = ['OWNER'], organizationIds: string[] = ['org-1'], allOrganizations = false): Company => ({
  id: 'acme',
  name: 'Acme Corp',
  roles,
  organizationIds,
  allOrganizations,
});

/** The auth context of a signed-in person looking at one company (or none). */
export function signedIn(options: { account?: Account; company?: Company | null; companies?: Company[]; refresh?: () => Promise<void> } = {}): Partial<AuthContextValue> {
  const account = options.account ?? ana;
  const company = options.company === undefined ? acme() : options.company;
  return {
    status: 'authenticated',
    mode: 'session',
    account,
    company,
    companies: options.companies ?? (company ? [company] : []),
    principal: principalFor(account, company),
    refresh: options.refresh ?? (async () => undefined),
  };
}

export const anonymous: Partial<AuthContextValue> = { status: 'anonymous', mode: 'session', account: null, principal: null, companies: [], company: null };
