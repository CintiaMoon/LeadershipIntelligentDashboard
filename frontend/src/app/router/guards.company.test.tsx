import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { acme, ana, anonymous, signedIn } from '@/tests/session';
import { renderRoutes } from '@/tests/utils';
import { RequireAuth, RequireCompany } from './guards';

afterEach(() => vi.unstubAllGlobals());

const guarded = [
  { path: '/', element: <RequireAuth><RequireCompany><p>the app</p></RequireCompany></RequireAuth> },
  { path: '/companies', element: <p>company picker</p> },
  { path: '/login', element: <p>login page</p> },
];

describe('RequireCompany', () => {
  it('lets a person in a company they may open through', () => {
    renderRoutes(guarded, { auth: signedIn({ company: acme() }) });
    expect(screen.getByText('the app')).toBeInTheDocument();
  });

  it('sends a person who is outside any company to choose one', async () => {
    renderRoutes(guarded, { auth: signedIn({ company: null, companies: [acme()] }) });
    expect(await screen.findByText('company picker')).toBeInTheDocument();
  });

  it('tells a person that they cannot open the company named in the address', () => {
    vi.stubGlobal('location', { ...window.location, pathname: '/c/initech/dashboard' });
    renderRoutes(guarded, { auth: signedIn({ company: null, companies: [acme()] }) });
    expect(screen.getByText('You cannot open this company')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'My companies' })).toHaveAttribute('href', '/companies');
    expect(screen.queryByText('the app')).not.toBeInTheDocument();
  });

  it('does not ask a platform administrator for a company', () => {
    renderRoutes(guarded, { auth: signedIn({ account: { ...ana, platformAdmin: true }, company: null, companies: [] }) });
    expect(screen.getByText('the app')).toBeInTheDocument();
  });


});

describe('RequireAuth', () => {
  it('sends an anonymous visitor to the sign-in page', async () => {
    renderRoutes(guarded, { auth: anonymous });
    expect(await screen.findByText('login page')).toBeInTheDocument();
  });

  it('leaves a company screen with a full page load that remembers where the person was', async () => {
    const replace = vi.fn();
    vi.stubGlobal('location', { ...window.location, pathname: '/c/acme/members', search: '?x=1', replace });
    renderRoutes(guarded, { auth: anonymous });
    await waitFor(() => expect(replace).toHaveBeenCalledWith('/login?from=%2Fc%2Facme%2Fmembers%3Fx%3D1'));
    vi.unstubAllEnvs();
  });
});

describe('RequireCompany and the platform administrator', () => {
  it('tells a platform administrator that a company address is not for them', () => {
    vi.stubGlobal('location', { ...window.location, pathname: '/c/acme/dashboard' });
    renderRoutes(guarded, { auth: signedIn({ account: { ...ana, platformAdmin: true }, company: null, companies: [] }) });
    expect(screen.getByText('You cannot open this company')).toBeInTheDocument();
    expect(screen.queryByText('the app')).not.toBeInTheDocument();
  });
});
