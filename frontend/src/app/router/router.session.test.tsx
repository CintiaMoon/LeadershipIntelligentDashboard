import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { anonymous } from '@/tests/session';
import { renderRoutes } from '@/tests/utils';
import { buildRoutes } from './router';

afterEach(() => vi.unstubAllEnvs());

const paths = () => buildRoutes().map((r) => r.path);

describe('the routes', () => {
  it('has the sign-in and the account pages, and nothing of the external provider', () => {
    expect(paths()).toEqual(expect.arrayContaining(['/login', '/register', '/verify-email', '/forgot-password', '/reset-password', '/invitations/accept', '/companies', '/account', '/']));
    expect(paths()).not.toContain('/auth/callback');
  });



  it('shows the email and password form with the application accounts', () => {
    renderRoutes(buildRoutes(), { route: '/login', auth: anonymous });
    expect(screen.getByLabelText('Email address')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
    expect(screen.queryByText('Choose a development persona')).not.toBeInTheDocument();
  });

  it('keeps the screens of a company on short paths under the router prefix', () => {
    const root = buildRoutes().find((r) => r.path === '/');
    const children = (root?.children ?? []).map((c) => c.path);
    expect(children).toEqual(expect.arrayContaining(['dashboard', 'members', 'api-tokens', 'security-audit', 'platform/security-audit', 'people', 'questionnaires', 'respond']));
  });
});
