import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useSession } from '@/stores/session';
import { acme, ana, signedIn } from '@/tests/session';
import { renderRoutes } from '@/tests/utils';
import { AppLayout } from './AppLayout';
import { navItems } from './nav';
import { principalFor } from '@/services/auth/principal';

const routes = [{ path: '/', element: <AppLayout />, children: [{ path: 'dashboard', element: <p>page body</p> }] }];

beforeEach(() => {
  useSession.setState({ organizationId: null, extraOrganizations: [] });
  return () => vi.unstubAllEnvs();
});

describe('AppLayout with the application accounts', () => {
  it('names the company, links to switch company and to the account, and offers only the organizations of the membership', () => {
    renderRoutes(routes, { route: '/dashboard', auth: signedIn({ company: acme(['VIEWER'], ['org-1', 'org-2']) }) });
    expect(screen.getByTestId('company-name')).toHaveTextContent('Acme Corp');
    // leaving a company is a full page load, so these are ordinary links
    expect(screen.getByRole('link', { name: 'Switch company' })).toHaveAttribute('href', '/companies');
    expect(screen.getByRole('link', { name: 'Ana Silva' })).toHaveAttribute('href', '/account');
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['org-1', 'org-2']);
    expect(screen.queryByRole('button', { name: 'New organization' })).not.toBeInTheDocument();
  });

  it('lets a member who is not limited to a list name a new organization', async () => {
    renderRoutes(routes, { route: '/dashboard', auth: signedIn({ company: acme(['OWNER'], [], true) }) });
    expect(screen.getByLabelText('Organization')).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'New organization' }));
    await userEvent.type(screen.getByLabelText('Organization id'), ' org-new ');
    await userEvent.click(screen.getByRole('button', { name: 'Use' }));
    expect(useSession.getState().organizationId).toBe('org-new');
    expect(screen.getByLabelText('Organization')).toHaveValue('org-new');
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['org-new']);
  });

  it('keeps the existing organizations beside a new one', async () => {
    renderRoutes(routes, { route: '/dashboard', auth: signedIn({ company: acme(['OWNER'], ['org-1'], true) }) });
    await userEvent.click(screen.getByRole('button', { name: 'New organization' }));
    await userEvent.type(screen.getByLabelText('Organization id'), 'org-2{Enter}');
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['org-1', 'org-2']);
  });

  it('does not offer a new organization to a member who is limited to a list', () => {
    renderRoutes(routes, { route: '/dashboard', auth: signedIn({ company: acme(['ADMIN'], ['org-1'], false) }) });
    expect(screen.queryByRole('button', { name: 'New organization' })).not.toBeInTheDocument();
  });

  it('freezes the top bar and shows where the page sits', () => {
    renderRoutes(routes, { route: '/dashboard', auth: signedIn({ company: acme(['VIEWER'], ['org-1']) }) });
    expect(screen.getByRole('banner')).toHaveClass('sticky', 'top-0');
    const trail = screen.getByRole('navigation', { name: 'Breadcrumb' });
    expect(trail).toHaveTextContent('Acme Corp');
    expect(trail.querySelector('[aria-current="page"]')).toHaveTextContent('Dashboard');
  });

  it('shows no company for the platform administrator', () => {
    renderRoutes(routes, { route: '/dashboard', auth: signedIn({ account: { ...ana, platformAdmin: true }, company: null, companies: [] }) });
    expect(screen.queryByTestId('company-name')).not.toBeInTheDocument();
    expect(screen.getByText('Platform administrator')).toBeInTheDocument();
  });
});

describe('the navigation with the application accounts', () => {
  const labels = (roles: string[]) => navItems(principalFor(ana, acme(roles))).map((i) => i.label);

  it('gives owners and administrators members, API tokens and the security audit', () => {
    for (const role of ['OWNER', 'ADMIN']) {
      expect(labels([role])).toEqual(expect.arrayContaining(['Members', 'API tokens', 'Security audit']));
    }
    vi.unstubAllEnvs();
  });

  it('gives nobody else any of them', () => {
    for (const role of ['ENGINEERING_LEADER', 'ANALYST', 'VIEWER']) {
      expect(labels([role])).not.toEqual(expect.arrayContaining(['Members']));
      expect(labels([role])).not.toContain('API tokens');
      expect(labels([role])).not.toContain('Security audit');
    }
    expect(navItems(principalFor(ana, acme(['RESPONDENT']))).map((i) => i.label)).toEqual(['My assessments']);
    vi.unstubAllEnvs();
  });

  it('gives the platform administrator the platform audit', () => {
    const items = navItems(principalFor({ ...ana, platformAdmin: true }, null));
    expect(items.map((i) => i.to)).toEqual(['/platform/tenants', '/configuration', '/platform/security-audit']);
    vi.unstubAllEnvs();
  });

});
