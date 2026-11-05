import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { API, configureSession, csrfHandler } from '@/tests/session';
import { renderRoutes } from '@/tests/utils';
import { CreateTenantPage } from './CreateTenantPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => {
  server.resetHandlers();
  vi.unstubAllEnvs();
});
afterAll(() => server.close());
beforeEach(() => {
  configureSession(null);
  server.use(csrfHandler);
});

const platform = { subject: 'root', tenantId: '', organizations: [], roles: [], platformAdmin: true };
const render = () =>
  renderRoutes([{ path: '/platform/tenants/new', element: <CreateTenantPage /> }, { path: '/platform/tenants', element: <p>tenant list page</p> }], {
    route: '/platform/tenants/new',
    auth: { principal: platform },
  });
const fill = async (owner: string) => {
  await userEvent.type(screen.getByLabelText('Tenant id'), 'initech');
  await userEvent.type(screen.getByLabelText('Name'), 'Initech');
  if (owner) await userEvent.type(screen.getByLabelText('Owner email address'), owner);
  await userEvent.click(screen.getByRole('button', { name: 'Create tenant' }));
};
const created = () => HttpResponse.json({ id: 'initech', name: 'Initech', createdAt: '2026-11-01T09:00:00Z' }, { status: 201 });

describe('CreateTenantPage with the application accounts', () => {
  it('invites the first owner by email', async () => {
    let body: unknown;
    server.use(http.post(`${API}/platform/tenants`, async ({ request }) => ((body = await request.json()), created())));
    render();
    await fill(' boss@example.com ');
    await screen.findByText('tenant list page');
    expect(body).toEqual({ id: 'initech', name: 'Initech', ownerEmail: 'boss@example.com' });
  });

  it('may leave the owner for later', async () => {
    let body: unknown;
    server.use(http.post(`${API}/platform/tenants`, async ({ request }) => ((body = await request.json()), created())));
    render();
    await fill('');
    await screen.findByText('tenant list page');
    expect(body).toEqual({ id: 'initech', name: 'Initech' });
  });

  it('does not send an owner address that is not one', async () => {
    let requests = 0;
    server.use(http.post(`${API}/platform/tenants`, () => ((requests += 1), created())));
    render();
    await fill('not-an-email');
    expect(await screen.findByText('Enter a valid email address.')).toBeInTheDocument();
    expect(requests).toBe(0);
  });

});
