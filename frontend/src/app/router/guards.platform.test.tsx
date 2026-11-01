import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderRoutes } from '@/tests/utils';
import { HomeRedirect, RequirePlatformAdmin } from './guards';

const platform = { subject: 'root', tenantId: '', organizations: [], roles: [], platformAdmin: true };

const routes = [
  { path: '/', element: <HomeRedirect /> },
  { path: '/dashboard', element: <p>dashboard page</p> },
  { path: '/platform/tenants', element: <p>tenant list page</p> },
  {
    path: '/platform/tenants/new',
    element: (
      <RequirePlatformAdmin>
        <p>create tenant page</p>
      </RequirePlatformAdmin>
    ),
  },
];

describe('RequirePlatformAdmin', () => {
  it('shows the page to the platform administrator', () => {
    renderRoutes(routes, { route: '/platform/tenants/new', auth: { principal: platform } });
    expect(screen.getByText('create tenant page')).toBeInTheDocument();
  });

  it.each([['OWNER'], ['ADMIN'], ['VIEWER']] as const)('tells a tenant %s that it is not for them and shows nothing of the page', (role) => {
    renderRoutes(routes, { route: '/platform/tenants/new', auth: { principal: { subject: 'u', tenantId: 't', organizations: ['org-1'], roles: [role] } } });
    expect(screen.getByRole('heading', { name: 'Only for platform administrators' })).toBeInTheDocument();
    expect(screen.queryByText('create tenant page')).not.toBeInTheDocument();
  });
});

describe('HomeRedirect', () => {
  it('sends a platform administrator to the tenants, who has no organization to look at', () => {
    renderRoutes(routes, { route: '/', auth: { principal: platform } });
    expect(screen.getByText('tenant list page')).toBeInTheDocument();
  });

  it('sends everyone else to the dashboard', () => {
    renderRoutes(routes, { route: '/' });
    expect(screen.getByText('dashboard page')).toBeInTheDocument();
  });
});
