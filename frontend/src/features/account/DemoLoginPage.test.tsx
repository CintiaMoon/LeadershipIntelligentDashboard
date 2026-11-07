import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { API, anonymous, csrfHandler, configureSession } from '@/tests/session';
import { renderRoutes } from '@/tests/utils';
import { DemoLoginPage } from './DemoLoginPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('DemoLoginPage (testing only)', () => {
  it('signs in as the chosen demo account with the published password, with no typing', async () => {
    configureSession('acme');
    let body: unknown;
    server.use(csrfHandler, http.post(`${API}/auth/login`, async ({ request }) => ((body = await request.json()), HttpResponse.json({ id: 'u', email: 'admin@demo.test', displayName: 'Admin', platformAdmin: false, companies: [] }))));
    const refresh = vi.fn(async () => undefined);
    renderRoutes([{ path: '/demo-login', element: <DemoLoginPage /> }], { route: '/demo-login', auth: { ...anonymous, refresh } });
    await userEvent.click(screen.getByRole('button', { name: 'Sign in as Administrator' }));
    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(body).toEqual({ email: 'admin@demo.test', password: 'demo-password-1234' });
  });

  it('lists every kind of account and says it is for testing', () => {
    renderRoutes([{ path: '/demo-login', element: <DemoLoginPage /> }], { route: '/demo-login', auth: anonymous });
    expect(screen.getByText(/exist only on a development stack/)).toBeInTheDocument();
    for (const label of ['Owner', 'Viewer', 'Respondent 6', 'Platform administrator', 'Administrator of another company']) {
      expect(screen.getByRole('button', { name: `Sign in as ${label}` })).toBeInTheDocument();
    }
  });
});
