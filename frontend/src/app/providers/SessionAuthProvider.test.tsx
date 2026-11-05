import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { apiFetch } from '@/services/api/client';
import { API, csrfHandler } from '@/tests/session';
import { testQueryClient } from '@/tests/utils';
import { useAuth } from './auth-context';
import { SessionAuthProvider } from './SessionAuthProvider';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => {
  server.resetHandlers();
  vi.unstubAllGlobals();
});
afterAll(() => server.close());

const ana = { id: 'u-1', email: 'ana@example.com', displayName: 'Ana', platformAdmin: false, companies: [{ id: 'acme', name: 'Acme Corp' }] };
const companies = [
  { id: 'acme', name: 'Acme Corp', roles: ['ADMIN'], organizationIds: ['org-1', 'org-2'], allOrganizations: false },
  { id: 'globex', name: 'Globex', roles: ['RESPONDENT'], organizationIds: [], allOrganizations: true },
];

function Probe() {
  const { status, principal, company, companies: list, signOut } = useAuth();
  return (
    <div>
      <p data-testid="status">{status}</p>
      <p data-testid="principal">{principal ? JSON.stringify(principal) : 'none'}</p>
      <p data-testid="company">{company?.name ?? 'none'}</p>
      <p data-testid="count">{list?.length ?? 0}</p>
      <button onClick={() => void signOut()}>out</button>
    </div>
  );
}

const mount = (tenantId: string | null) =>
  render(
    <QueryClientProvider client={testQueryClient()}>
      <SessionAuthProvider tenantId={tenantId}>
        <Probe />
      </SessionAuthProvider>
    </QueryClientProvider>,
  );

describe('SessionAuthProvider', () => {
  it('reads the account and the companies, and takes what the user may do from the company in the address', async () => {
    server.use(http.get(`${API}/auth/me`, () => HttpResponse.json(ana)), http.get(`${API}/companies`, () => HttpResponse.json(companies)));
    mount('acme');
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));
    expect(screen.getByTestId('company')).toHaveTextContent('Acme Corp');
    expect(screen.getByTestId('count')).toHaveTextContent('2');
    expect(JSON.parse(screen.getByTestId('principal').textContent ?? '')).toMatchObject({ tenantId: 'acme', roles: ['ADMIN'], organizations: ['org-1', 'org-2'] });
  });

  it('marks a respondent from the role of the membership', async () => {
    server.use(http.get(`${API}/auth/me`, () => HttpResponse.json(ana)), http.get(`${API}/companies`, () => HttpResponse.json(companies)));
    mount('globex');
    await waitFor(() => expect(screen.getByTestId('company')).toHaveTextContent('Globex'));
    expect(JSON.parse(screen.getByTestId('principal').textContent ?? '')).toMatchObject({ respondent: true, roles: [] });
  });

  it('has no company, and so no principal, outside a company or for one the user cannot open', async () => {
    server.use(http.get(`${API}/auth/me`, () => HttpResponse.json(ana)), http.get(`${API}/companies`, () => HttpResponse.json(companies)));
    mount('initech');
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));
    expect(screen.getByTestId('company')).toHaveTextContent('none');
    expect(screen.getByTestId('principal')).toHaveTextContent('none');
  });

  it('does not ask a platform administrator for companies, and gives them the platform principal', async () => {
    let asked = false;
    server.use(
      http.get(`${API}/auth/me`, () => HttpResponse.json({ ...ana, platformAdmin: true, companies: [] })),
      http.get(`${API}/companies`, () => ((asked = true), HttpResponse.json([]))),
    );
    mount(null);
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));
    expect(asked).toBe(false);
    expect(JSON.parse(screen.getByTestId('principal').textContent ?? '')).toMatchObject({ platformAdmin: true, tenantId: '' });
  });

  it('is anonymous when there is no session', async () => {
    server.use(http.get(`${API}/auth/me`, () => HttpResponse.json({ code: 'UNAUTHENTICATED' }, { status: 401 })));
    mount(null);
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'));
    expect(screen.getByTestId('principal')).toHaveTextContent('none');
  });

  it('becomes anonymous when a call finds the session over', async () => {
    server.use(
      http.get(`${API}/auth/me`, () => HttpResponse.json(ana)),
      http.get(`${API}/companies`, () => HttpResponse.json(companies)),
      http.get(`${API}/assessments`, () => HttpResponse.json({ code: 'UNAUTHENTICATED' }, { status: 401 })),
    );
    mount('acme');
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));
    await expect(apiFetch('/assessments')).rejects.toMatchObject({ status: 401 });
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'));
    expect(screen.getByTestId('count')).toHaveTextContent('0');
  });

  it('names the company of the address on every call', async () => {
    let header: string | null = null;
    server.use(
      http.get(`${API}/auth/me`, () => HttpResponse.json(ana)),
      http.get(`${API}/companies`, () => HttpResponse.json(companies)),
      http.get(`${API}/assessments`, ({ request }) => ((header = request.headers.get('X-Company-Id')), HttpResponse.json({ items: [] }))),
    );
    mount('acme');
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));
    await apiFetch('/assessments');
    expect(header).toBe('acme');
  });

  it('signs out on the server and leaves with a full page load', async () => {
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign });
    let loggedOut = false;
    server.use(
      csrfHandler,
      http.get(`${API}/auth/me`, () => HttpResponse.json(ana)),
      http.get(`${API}/companies`, () => HttpResponse.json(companies)),
      http.post(`${API}/auth/logout`, () => ((loggedOut = true), new HttpResponse(null, { status: 204 }))),
    );
    mount('acme');
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));
    await userEvent.click(screen.getByRole('button', { name: 'out' }));
    await waitFor(() => expect(assign).toHaveBeenCalledWith('/login'));
    expect(loggedOut).toBe(true);
  });
});
