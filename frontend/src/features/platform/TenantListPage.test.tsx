import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { configureApi } from '@/services/api/client';
import { ORIGIN } from '@/tests/msw';
import { renderRoutes } from '@/tests/utils';
import { TenantListPage } from './TenantListPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => configureApi({ getToken: () => 'tok', onUnauthorized: () => undefined }));

const platform = { subject: 'root', tenantId: '', organizations: [], roles: [], platformAdmin: true };
const routes = [
  { path: '/platform/tenants', element: <TenantListPage /> },
  { path: '/platform/tenants/new', element: <p>create page</p> },
];
const render = (route = '/platform/tenants') => renderRoutes(routes, { route, auth: { principal: platform } });

const items = [
  { id: 'acme', name: 'Acme Corporation', createdAt: '2026-10-31T17:32:13Z' },
  { id: 'globex', name: 'Globex', createdAt: '2026-11-01T09:00:00Z' },
];

describe('TenantListPage', () => {
  it('lists every tenant with its id, name and creation date, in the order returned', async () => {
    server.use(http.get(`${ORIGIN}/platform/tenants`, () => HttpResponse.json({ items })));
    render();
    expect(screen.getByRole('status')).toHaveTextContent('Loading the tenants');
    const table = await screen.findByRole('table', { name: 'Tenants, oldest first' });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent('acme');
    expect(rows[1]).toHaveTextContent('Acme Corporation');
    expect(rows[2]).toHaveTextContent('globex');
    expect(screen.getByText('2 of 2 tenants')).toBeInTheDocument();
  });

  it('offers to create a tenant', async () => {
    server.use(http.get(`${ORIGIN}/platform/tenants`, () => HttpResponse.json({ items })));
    const view = render();
    await userEvent.click(screen.getByRole('link', { name: 'Create tenant' }));
    expect(view.router.state.location.pathname).toBe('/platform/tenants/new');
  });

  it('filters by id or name and says when nothing matches', async () => {
    server.use(http.get(`${ORIGIN}/platform/tenants`, () => HttpResponse.json({ items })));
    render();
    await screen.findByRole('table');
    await userEvent.type(screen.getByRole('searchbox', { name: 'Filter' }), 'GLOBE');
    expect(screen.getByText('1 of 2 tenants')).toBeInTheDocument();
    expect(screen.queryByText('Acme Corporation')).not.toBeInTheDocument();
    await userEvent.clear(screen.getByRole('searchbox', { name: 'Filter' }));
    await userEvent.type(screen.getByRole('searchbox', { name: 'Filter' }), 'nothing');
    expect(screen.getByText(/No tenant matches/)).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('has an empty state that leads to creating the first tenant', async () => {
    server.use(http.get(`${ORIGIN}/platform/tenants`, () => HttpResponse.json({ items: [] })));
    render();
    expect(await screen.findByRole('heading', { name: 'No tenants yet' })).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Create tenant' }).length).toBeGreaterThan(0);
  });

  it('reports a failure and retries', async () => {
    let calls = 0;
    server.use(
      http.get(`${ORIGIN}/platform/tenants`, () => {
        calls += 1;
        return calls === 1 ? HttpResponse.json({ code: 'X', message: 'boom', correlationId: 'ten-1' }, { status: 503 }) : HttpResponse.json({ items });
      }),
    );
    render();
    expect(await screen.findByText('The service is temporarily unavailable')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Try again|Retry/ }));
    expect(await screen.findByRole('table')).toBeInTheDocument();
  });

  it('confirms a tenant that was just created', async () => {
    server.use(http.get(`${ORIGIN}/platform/tenants`, () => HttpResponse.json({ items })));
    const view = render();
    await view.router.navigate('/platform/tenants', { state: { created: 'acme' } });
    expect(await screen.findByText(/was created/)).toHaveTextContent('Tenant acme was created');
  });

  it('refreshes on request', async () => {
    let calls = 0;
    server.use(
      http.get(`${ORIGIN}/platform/tenants`, () => {
        calls += 1;
        return HttpResponse.json({ items });
      }),
    );
    render();
    await screen.findByRole('table');
    await userEvent.click(screen.getByRole('button', { name: 'Refresh' }));
    await screen.findByText('2 of 2 tenants');
    expect(calls).toBe(2);
  });

  it('never requests the list for someone who is not a platform administrator', async () => {
    let requested = false;
    server.use(
      http.get(`${ORIGIN}/platform/tenants`, () => {
        requested = true;
        return HttpResponse.json({ items });
      }),
    );
    renderRoutes(routes, { route: '/platform/tenants' });
    await new Promise((r) => setTimeout(r, 50));
    expect(requested).toBe(false);
  });
});
