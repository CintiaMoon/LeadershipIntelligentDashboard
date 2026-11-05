import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { acme, API, configureSession, csrfHandler, signedIn } from '@/tests/session';
import { renderRoutes } from '@/tests/utils';
import type { ApiToken } from '@/types/identity';
import { ApiTokensPage } from './ApiTokensPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => {
  server.resetHandlers();
  vi.unstubAllGlobals();
});
afterAll(() => server.close());
beforeEach(() => {
  configureSession('acme');
  server.use(csrfHandler);
});

const SECRET = 'lis_aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456789abcdefg';

const token = (over: Partial<ApiToken>): ApiToken => ({
  id: 't-1',
  name: 'Nightly export',
  role: 'ANALYST',
  prefix: 'lis_aBcDeFgH',
  organizationIds: [],
  createdBy: 'ana@example.com',
  createdAt: '2026-11-01T09:00:00Z',
  expiresAt: null,
  lastUsedAt: null,
  revokedAt: null,
  status: 'ACTIVE',
  ...over,
});

function open(list: ApiToken[] = []) {
  server.use(http.get(`${API}/api-tokens`, () => HttpResponse.json(list)));
  return renderRoutes([{ path: '/api-tokens', element: <ApiTokensPage /> }], { route: '/api-tokens', auth: signedIn({ company: acme(['OWNER'], ['org-1']) }) });
}

describe('ApiTokensPage', () => {
  it('lists the tokens with their role, status and use, never a secret', async () => {
    open([token({}), token({ id: 't-2', name: 'Old', status: 'REVOKED', revokedAt: '2026-11-02T09:00:00Z' }), token({ id: 't-3', name: 'Lapsed', status: 'EXPIRED', expiresAt: '2026-10-01T00:00:00Z' })]);
    const items = await screen.findAllByRole('listitem');
    const first = items[0] as HTMLElement;
    expect(within(first).getByText('Active')).toBeInTheDocument();
    expect(within(first).getByText('Analyst')).toBeInTheDocument();
    expect(within(first).getByText(/lis_aBcDeFgH/)).toBeInTheDocument();
    expect(within(first).getByText(/Last used never/)).toBeInTheDocument();
    expect(within(first).getByRole('button', { name: 'Revoke' })).toBeInTheDocument();
    // a token that no longer works has nothing to revoke
    const old = items[1] as HTMLElement;
    expect(within(old).getByText('Revoked')).toBeInTheDocument();
    expect(within(old).queryByRole('button', { name: 'Revoke' })).not.toBeInTheDocument();
    expect(within(items[2] as HTMLElement).getByText('Expired')).toBeInTheDocument();
    expect(document.body.textContent).not.toContain('secret');
  });

  it('never offers the owner role to a token', async () => {
    open();
    await screen.findByText('No API tokens');
    const options = within(screen.getByLabelText('Role')).getAllByRole('option').map((o) => o.textContent);
    expect(options).toEqual(['Admin', 'Engineering leader', 'Analyst', 'Viewer']);
  });

  it('creates a token, shows its secret once, and keeps it out of the list', async () => {
    let body: unknown;
    let listed = [] as ApiToken[];
    server.use(
      http.post(`${API}/api-tokens`, async ({ request }) => {
        body = await request.json();
        listed = [token({ name: 'CI pipeline', role: 'VIEWER', expiresAt: '2027-01-31T23:59:59Z', organizationIds: ['org-1'] })];
        return HttpResponse.json({ token: listed[0], secret: SECRET }, { status: 201 });
      }),
    );
    server.use(http.get(`${API}/api-tokens`, () => HttpResponse.json(listed)));
    renderRoutes([{ path: '/api-tokens', element: <ApiTokensPage /> }], { route: '/api-tokens', auth: signedIn({ company: acme(['OWNER'], ['org-1']) }) });
    await screen.findByText('No API tokens');
    await userEvent.type(screen.getByLabelText('Name'), '  CI pipeline ');
    await userEvent.selectOptions(screen.getByLabelText('Role'), 'VIEWER');
    await userEvent.type(screen.getByLabelText(/Expires/), '2027-01-31');
    await userEvent.type(screen.getByLabelText('Limited to organizations'), 'org-1');
    await userEvent.click(screen.getByRole('button', { name: 'Create the token' }));

    const panel = await screen.findByRole('region', { name: 'New API token' });
    expect(within(panel).getByLabelText('Secret')).toHaveValue(SECRET);
    expect(within(panel).getByText(/shown only this once/)).toBeInTheDocument();
    expect(body).toEqual({ name: 'CI pipeline', role: 'VIEWER', expiresAt: '2027-01-31T23:59:59Z', organizationIds: ['org-1'] });
    // the list that follows holds the token without the secret
    await screen.findByText(/CI pipeline/, { selector: 'p' }).catch(() => undefined);
    await waitFor(() => expect(screen.getAllByText(/lis_aBcDeFgH/).length).toBeGreaterThan(0));
    expect(screen.getAllByDisplayValue(SECRET)).toHaveLength(1);

    // once the person says they saved it, it is gone from the page
    await userEvent.click(screen.getByRole('button', { name: 'I have saved it' }));
    expect(screen.queryByDisplayValue(SECRET)).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain(SECRET);
  });

  it('copies the secret on request', async () => {
    const writeText = vi.fn(async () => undefined);
    vi.stubGlobal('navigator', { ...window.navigator, clipboard: { writeText } });
    server.use(http.post(`${API}/api-tokens`, () => HttpResponse.json({ token: token({}), secret: SECRET }, { status: 201 })));
    open();
    await screen.findByText('No API tokens');
    await userEvent.type(screen.getByLabelText('Name'), 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Create the token' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Copy' }));
    expect(writeText).toHaveBeenCalledWith(SECRET);
    expect(await screen.findByRole('button', { name: 'Copied' })).toBeInTheDocument();
  });

  it('needs a name', async () => {
    open();
    await screen.findByText('No API tokens');
    expect(screen.getByRole('button', { name: 'Create the token' })).toBeDisabled();
  });

  it('shows why a token was refused', async () => {
    server.use(http.post(`${API}/api-tokens`, () => HttpResponse.json({ code: 'INVALID_API_TOKEN', message: 'A token cannot have a stronger role than the person who creates it.' }, { status: 422 })));
    open();
    await screen.findByText('No API tokens');
    await userEvent.type(screen.getByLabelText('Name'), 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Create the token' }));
    expect(await screen.findByText(/stronger role than the person/)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'New API token' })).not.toBeInTheDocument();
  });

  it('asks before revoking, and revokes', async () => {
    let revoked: string | undefined;
    server.use(http.delete(`${API}/api-tokens/:id`, ({ params }) => ((revoked = String(params.id)), new HttpResponse(null, { status: 204 }))));
    open([token({})]);
    const item = (await screen.findAllByRole('listitem'))[0] as HTMLElement;
    await userEvent.click(within(item).getByRole('button', { name: 'Revoke' }));
    expect(revoked).toBeUndefined();
    await userEvent.click(within(item).getByRole('button', { name: 'Yes' }));
    await waitFor(() => expect(revoked).toBe('t-1'));
  });
});
