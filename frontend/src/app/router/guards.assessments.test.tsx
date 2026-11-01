import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Principal, Role } from '@/services/auth/principal';
import { renderRoutes } from '@/tests/utils';
import { HomeRedirect, RequireManager, RequireRespondent } from './guards';

const tenant = (...roles: Role[]): Principal => ({ subject: 'u', tenantId: 't', organizations: ['org-1'], roles });
const respondent: Principal = { subject: 'r', tenantId: 't', organizations: [], roles: [], respondent: true };

const routes = [
  { path: '/', element: <HomeRedirect /> },
  { path: '/dashboard', element: <p>dashboard page</p> },
  { path: '/respond', element: <p>respond page</p> },
  { path: '/platform/tenants', element: <p>tenant list page</p> },
  { path: '/questionnaires', element: <RequireManager><p>library page</p></RequireManager> },
  { path: '/answer', element: <RequireRespondent><p>answer page</p></RequireRespondent> },
];

describe('RequireManager', () => {
  it.each([['OWNER'], ['ADMIN']] as const)('shows the page to an %s', (role) => {
    renderRoutes(routes, { route: '/questionnaires', auth: { principal: tenant(role) } });
    expect(screen.getByText('library page')).toBeInTheDocument();
  });

  it.each([['ENGINEERING_LEADER'], ['ANALYST'], ['VIEWER']] as const)('tells a %s that it is not for them and shows nothing of the page', (role) => {
    renderRoutes(routes, { route: '/questionnaires', auth: { principal: tenant(role) } });
    expect(screen.getByRole('heading', { name: 'Only for owners and administrators' })).toBeInTheDocument();
    expect(screen.queryByText('library page')).not.toBeInTheDocument();
  });

  it('keeps a respondent out as well', () => {
    renderRoutes(routes, { route: '/questionnaires', auth: { principal: respondent } });
    expect(screen.getByRole('heading', { name: 'Only for owners and administrators' })).toBeInTheDocument();
  });
});

describe('RequireRespondent', () => {
  it('shows the page to a respondent', () => {
    renderRoutes(routes, { route: '/answer', auth: { principal: respondent } });
    expect(screen.getByText('answer page')).toBeInTheDocument();
  });

  it.each([['OWNER'], ['ADMIN'], ['VIEWER']] as const)('tells an %s that it is not for them', (role) => {
    renderRoutes(routes, { route: '/answer', auth: { principal: tenant(role) } });
    expect(screen.getByRole('heading', { name: 'Only for respondents' })).toBeInTheDocument();
    expect(screen.queryByText('answer page')).not.toBeInTheDocument();
  });
});

describe('HomeRedirect for a respondent', () => {
  it('sends a respondent to their assessments, who has no dashboard to look at', () => {
    renderRoutes(routes, { route: '/', auth: { principal: respondent } });
    expect(screen.getByText('respond page')).toBeInTheDocument();
  });

  it('still sends everyone else to the dashboard', () => {
    renderRoutes(routes, { route: '/', auth: { principal: tenant('ADMIN') } });
    expect(screen.getByText('dashboard page')).toBeInTheDocument();
  });
});
