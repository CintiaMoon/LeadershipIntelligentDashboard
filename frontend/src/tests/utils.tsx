import type { ReactElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router-dom';
import { AuthContext, type AuthContextValue } from '@/app/providers/auth-context';
import type { Principal } from '@/services/auth/principal';

export const adminPrincipal: Principal = { subject: 'admin-user', tenantId: 'demo-tenant', organizations: ['org-1', 'org-2'], roles: ['ADMIN'] };

/** A signed (but unverified) token, enough for decodePrincipal. */
export function fakeToken(claims: Record<string, unknown>): string {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none' })}.${encode(claims)}.sig`;
}

export function testQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } } });
}

export function renderRoutes(routes: RouteObject[], options: { route?: string; auth?: Partial<AuthContextValue> } = {}) {
  const auth: AuthContextValue = {
    status: 'authenticated',
    principal: adminPrincipal,
    signIn: async () => undefined,
    signOut: async () => undefined,
    ...options.auth,
  };
  const router = createMemoryRouter(routes, { initialEntries: [options.route ?? '/'] });
  const client = testQueryClient();
  const view = render(
    <QueryClientProvider client={client}>
      <AuthContext.Provider value={auth}>
        <RouterProvider router={router} />
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
  return { ...view, router, auth };
}

export function renderWithQuery(ui: ReactElement) {
  return render(<QueryClientProvider client={testQueryClient()}>{ui}</QueryClientProvider>);
}
