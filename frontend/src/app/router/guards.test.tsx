import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderRoutes } from '@/tests/utils';
import { canScore } from '@/services/auth/principal';
import { Permitted, RequireAuth } from './guards';

const routes = [
  { path: '/login', element: <p>login page</p> },
  {
    path: '/secret',
    element: (
      <RequireAuth>
        <p>secret content</p>
        <Permitted check={canScore} fallback={<p>read only</p>}>
          <button>Score</button>
        </Permitted>
      </RequireAuth>
    ),
  },
];

describe('RequireAuth', () => {
  it('sends anonymous visitors to the login page', async () => {
    const { router } = renderRoutes(routes, { route: '/secret', auth: { status: 'anonymous', principal: null } });
    expect(await screen.findByText('login page')).toBeInTheDocument();
    expect(router.state.location.state).toEqual({ from: '/secret' });
  });

  it('shows a loading state while the session is checked', () => {
    renderRoutes(routes, { route: '/secret', auth: { status: 'loading', principal: null } });
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText('secret content')).not.toBeInTheDocument();
  });

  it('renders protected content for a signed-in user', () => {
    renderRoutes(routes, { route: '/secret' });
    expect(screen.getByText('secret content')).toBeInTheDocument();
  });
});

describe('Permitted', () => {
  it('shows the action to roles that may use it', () => {
    renderRoutes(routes, { route: '/secret' });
    expect(screen.getByRole('button', { name: 'Score' })).toBeInTheDocument();
  });

  it('shows the fallback to roles that may not', () => {
    renderRoutes(routes, { route: '/secret', auth: { principal: { subject: 'v', tenantId: 't', organizations: ['org-1'], roles: ['VIEWER'] } } });
    expect(screen.queryByRole('button', { name: 'Score' })).not.toBeInTheDocument();
    expect(screen.getByText('read only')).toBeInTheDocument();
  });
});
