import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { useSession } from '@/stores/session';
import { acme, API, configureSession, csrfHandler, signedIn } from '@/tests/session';
import { renderRoutes } from '@/tests/utils';
import { PeoplePage } from './PeoplePage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => {
  server.resetHandlers();
  vi.unstubAllEnvs();
});
afterAll(() => server.close());
beforeEach(() => {
  vi.stubEnv('VITE_AUTH_MODE', 'session');
  configureSession('acme');
  server.use(csrfHandler);
  useSession.setState({ organizationId: 'org-1' });
  server.use(
    http.get(`${API}/teams`, () => HttpResponse.json([{ id: 't-a', organizationId: 'org-1', name: 'Alpha', activePeople: 1 }])),
    http.get(`${API}/people`, () =>
      HttpResponse.json([
        { id: 'p-1', teamId: 't-a', organizationId: 'org-1', name: 'Ada Lovelace', email: 'ada@acme.test', active: true },
        { id: 'p-2', teamId: 't-a', organizationId: 'org-1', name: 'Grace Hopper', email: 'grace@acme.test', active: false },
      ]),
    ),
  );
});

const render = () => renderRoutes([{ path: '/people', element: <PeoplePage /> }], { route: '/people', auth: signedIn({ company: acme(['OWNER'], ['org-1']) }) });

describe('PeoplePage invitations to answer', () => {
  it('invites an active person to answer, at the address on the roster', async () => {
    let invited: string | undefined;
    server.use(http.post(`${API}/people/:id/invite`, ({ params }) => ((invited = String(params.id)), HttpResponse.json({ id: 'm-1', email: 'ada@acme.test' }, { status: 201 }))));
    render();
    const card = await screen.findByRole('region', { name: 'Team Alpha' });
    await userEvent.click(within(card).getByRole('button', { name: 'Invite Ada Lovelace to answer' }));
    await waitFor(() => expect(invited).toBe('p-1'));
    const done = await within(card).findByRole('button', { name: 'Invite Ada Lovelace to answer' });
    await waitFor(() => expect(done).toHaveTextContent('Invited'));
    expect(done).toBeDisabled();
  });

  it('does not offer it for someone who is not active', async () => {
    render();
    const card = await screen.findByRole('region', { name: 'Team Alpha' });
    expect(within(card).queryByRole('button', { name: 'Invite Grace Hopper to answer' })).not.toBeInTheDocument();
  });

  it('shows why an invitation was refused', async () => {
    server.use(http.post(`${API}/people/:id/invite`, () => HttpResponse.json({ code: 'MEMBER_CONFLICT', message: 'This person is already a member of the company.' }, { status: 409 })));
    render();
    const card = await screen.findByRole('region', { name: 'Team Alpha' });
    await userEvent.click(within(card).getByRole('button', { name: 'Invite Ada Lovelace to answer' }));
    expect(await within(card).findByText(/already a member of the company/)).toBeInTheDocument();
  });

  it('is not offered with the external provider, which has no members to invite', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'oidc');
    render();
    const card = await screen.findByRole('region', { name: 'Team Alpha' });
    expect(within(card).queryByRole('button', { name: /to answer/ })).not.toBeInTheDocument();
  });
});
