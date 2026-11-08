import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { buildRoutes } from '@/app/router/router';
import { anonymous } from '@/tests/session';
import { renderRoutes } from '@/tests/utils';

describe('the landing page at the root of the site', () => {
  it('tells an anonymous visitor what the product is and offers to sign in or create an account', () => {
    renderRoutes(buildRoutes(), { route: '/', auth: anonymous });
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/how your teams are really led/i);
    expect(screen.getAllByRole('link', { name: 'Sign in' })[0]).toHaveAttribute('href', '/login');
    expect(screen.getAllByRole('link', { name: 'Create an account' })[0]).toHaveAttribute('href', '/register');
    expect(screen.getByRole('heading', { name: 'Four dimensions, one score' })).toBeInTheDocument();
  });

  it('is not the page a visitor reaches at any other address: the application still asks for a sign-in', () => {
    renderRoutes(buildRoutes(), { route: '/dashboard', auth: anonymous });
    expect(screen.queryByRole('heading', { level: 1, name: /really led/i })).not.toBeInTheDocument();
  });

  it('does not offer the test accounts in a normal build', () => {
    renderRoutes(buildRoutes(), { route: '/', auth: anonymous });
    expect(screen.queryByRole('link', { name: 'Test accounts' })).not.toBeInTheDocument();
  });
});
