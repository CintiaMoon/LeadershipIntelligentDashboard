import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useSession } from '@/stores/session';
import { renderRoutes } from '@/tests/utils';
import { AppLayout } from './AppLayout';

const routes = [{ path: '/', element: <AppLayout />, children: [{ path: 'dashboard', element: <p>page body</p> }] }];

beforeEach(() => useSession.setState({ organizationId: null }));

describe('AppLayout', () => {
  it('lists the organizations of the token and selects the first one', async () => {
    renderRoutes(routes, { route: '/dashboard' });
    const select = screen.getByLabelText('Organization');
    expect(select).toHaveValue('org-1');
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['org-1', 'org-2']);
    expect(screen.getByText('page body')).toBeInTheDocument();
  });

  it('changes the selected organization', async () => {
    renderRoutes(routes, { route: '/dashboard' });
    await userEvent.selectOptions(screen.getByLabelText('Organization'), 'org-2');
    expect(useSession.getState().organizationId).toBe('org-2');
  });

  it('drops a remembered organization that this user does not have', () => {
    useSession.setState({ organizationId: 'org-9' });
    renderRoutes(routes, { route: '/dashboard' });
    expect(useSession.getState().organizationId).toBe('org-1');
  });

  it('shows the roles and signs out', async () => {
    const signOut = vi.fn(async () => undefined);
    renderRoutes(routes, { route: '/dashboard', auth: { signOut } });
    expect(screen.getByText('Admin')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    expect(signOut).toHaveBeenCalled();
  });

  it('offers a skip link and a labelled main navigation', () => {
    renderRoutes(routes, { route: '/dashboard' });
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main');
    expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument();
  });
});

describe('AppLayout for the platform administrator', () => {
  const platform = { subject: 'root', tenantId: '', organizations: [], roles: [], platformAdmin: true };
  const platformRoutes = [{ path: '/', element: <AppLayout />, children: [{ path: 'platform/tenants', element: <p>tenant list</p> }] }];

  it('offers the tenants, and neither the dashboard, the comparison nor an organization to choose', () => {
    renderRoutes(platformRoutes, { route: '/platform/tenants', auth: { principal: platform } });
    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(nav).toHaveTextContent('Tenants');
    expect(nav).not.toHaveTextContent('Dashboard');
    expect(nav).not.toHaveTextContent('Compare');
    expect(screen.queryByLabelText('Organization')).not.toBeInTheDocument();
    expect(screen.getByText('Platform administrator')).toBeInTheDocument();
  });

  it('does not offer the tenants to anyone else', () => {
    renderRoutes(routes, { route: '/dashboard' });
    expect(screen.queryByRole('link', { name: 'Tenants' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Dashboard' })).toBeInTheDocument();
  });
});
