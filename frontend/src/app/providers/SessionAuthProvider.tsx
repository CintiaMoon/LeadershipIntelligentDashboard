import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { configureApi } from '@/services/api/client';
import { identityApi } from '@/services/api/identity';
import { principalFor } from '@/services/auth/principal';
import type { Account, Company } from '@/types/identity';
import { AuthContext, type AuthStatus } from './auth-context';

interface Loaded {
  account: Account;
  companies: Company[];
}

/** The signed-in account and the companies it may open, or null when there is no session. */
async function fetchSession(): Promise<Loaded | null> {
  try {
    const account = await identityApi.me();
    // a platform administrator belongs to no company and the company list is not for them
    const companies = account.platformAdmin ? [] : await identityApi.companies();
    return { account, companies };
  } catch {
    return null;
  }
}

/**
 * Signed in with the application's own account (decisions D65 to D69). The session is a cookie the browser keeps; nothing secret is
 * readable here. The user is read from {@code /auth/me} and the companies they may open from {@code /companies}. The company of a
 * screen is the one in the address ({@code tenantId}); the roles and organizations come from that membership and are only used to shape
 * the interface, because the server checks the membership again on every call.
 */
export function SessionAuthProvider({ tenantId, children }: { tenantId: string | null; children: ReactNode }) {
  const queries = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [account, setAccount] = useState<Account | null>(null);
  const [companies, setCompanies] = useState<Company[]>([]);

  const apply = useCallback((loaded: Loaded | null) => {
    setAccount(loaded?.account ?? null);
    setCompanies(loaded?.companies ?? []);
    setStatus(loaded ? 'authenticated' : 'anonymous');
  }, []);

  const forget = useCallback(() => {
    apply(null);
    queries.clear();
  }, [apply, queries]);

  // The credential is the cookie, so there is no token: the client only needs to know the company and to send the CSRF token.
  // Done while rendering, before any child can ask for data.
  useState(() => configureApi({ onUnauthorized: () => forget(), getCompany: () => tenantId, csrf: true }));

  const refresh = useCallback(async () => apply(await fetchSession()), [apply]);

  useEffect(() => {
    let active = true;
    void fetchSession().then((loaded) => {
      if (active) apply(loaded);
    });
    return () => {
      active = false;
    };
  }, [apply]);

  const signOut = useCallback(async () => {
    try {
      await identityApi.logout();
    } finally {
      // a full load leaves nothing of the session behind: no cache, no state
      window.location.assign('/login');
    }
  }, []);

  const signIn = useCallback(async () => {
    window.location.assign('/login');
  }, []);

  const company = useMemo(() => companies.find((c) => c.id === tenantId) ?? null, [companies, tenantId]);
  const principal = useMemo(() => (account ? principalFor(account, company) : null), [account, company]);

  const value = useMemo(
    () => ({ status, principal, signIn, signOut, account, companies, company, refresh }),
    [status, principal, signIn, signOut, account, companies, company, refresh],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
