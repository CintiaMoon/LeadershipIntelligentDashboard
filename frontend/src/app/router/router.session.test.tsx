import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { anonymous } from '@/tests/session';
import { renderRoutes } from '@/tests/utils';
import { buildRoutes } from './router';

afterEach(() => vi.unstubAllEnvs());

const paths = () => buildRoutes().map((r) => r.path);

describe('the routes of each way of signing in', () => {
  it('has the account pages and no provider callback with the application accounts', () => {
    vi.stubEnv('VITE_AUTH_MODE', 'session');
    expect(paths()).toEqual(expect.arrayContaining(['/login', '/register', '/verify-email', '/forgot-password', '/reset-password', '/invitations/accept', '/companies', '/account', '/']));
    expect(paths()).not.toContain('/auth/callback');
  });

  it('has the provider callback and none of the account pages with the external provider', () => {
    vi.stubEnv('VITE_AUTH_MODE', 'oidc');
    expect(paths()).toEqual(expect.arrayContaining(['/login', '/auth/callback', '/']));
    for (const account of ['/register', '/verify-email', '/forgot-password', '/reset-password', '/invitations/accept', '/companies', '/account']) {
      expect(paths()).not.toContain(account);
    }
  });

  it('shows the email and password form with the application accounts', () => {
    vi.stubEnv('VITE_AUTH_MODE', 'session');
    renderRoutes(buildRoutes(), { route: '/login', auth: anonymous });
    expect(screen.getByLabelText('Email address')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
    expect(screen.queryByText('Choose a development persona')).not.toBeInTheDocument();
  });

  it('keeps the screens of a company on short paths under the router prefix', () => {
    vi.stubEnv('VITE_AUTH_MODE', 'session');
    const root = buildRoutes().find((r) => r.path === '/');
    const children = (root?.children ?? []).map((c) => c.path);
    expect(children).toEqual(expect.arrayContaining(['dashboard', 'members', 'api-tokens', 'security-audit', 'platform/security-audit', 'people', 'questionnaires', 'respond']));
  });
});
