import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { User } from 'oidc-client-ts';
import { configureApi } from '@/services/api/client';
import { authConfig } from '@/services/auth/config';
import { getUserManager, rememberPersona, rememberReturnTo } from '@/services/auth/oidc';
import { decodePrincipal, type Principal } from '@/services/auth/principal';
import { AuthContext, type AuthStatus } from './auth-context';

function principalOf(user: User | null): Principal | null {
  return user && !user.expired ? decodePrincipal(user.access_token) : null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [principal, setPrincipal] = useState<Principal | null>(null);
  const tokenRef = useRef<string | null>(null);

  const apply = useCallback((user: User | null) => {
    const next = principalOf(user);
    tokenRef.current = next ? (user?.access_token ?? null) : null;
    setPrincipal(next);
    setStatus(next ? 'authenticated' : 'anonymous');
  }, []);

  const clear = useCallback(() => {
    void getUserManager().removeUser();
    apply(null);
  }, [apply]);

  useEffect(() => {
    configureApi({ getToken: () => tokenRef.current, onUnauthorized: clear });
    const manager = getUserManager();
    const onLoaded = (user: User) => apply(user);
    // Removing a user raises this event too, so it must only update state, never remove the user again.
    const onUnloaded = () => apply(null);
    manager.events.addUserLoaded(onLoaded);
    manager.events.addAccessTokenExpired(clear);
    manager.events.addUserUnloaded(onUnloaded);
    let active = true;
    manager
      .getUser()
      .then((user) => active && apply(user))
      .catch(() => active && apply(null));
    return () => {
      active = false;
      manager.events.removeUserLoaded(onLoaded);
      manager.events.removeAccessTokenExpired(clear);
      manager.events.removeUserUnloaded(onUnloaded);
    };
  }, [apply, clear]);

  const signIn = useCallback(async (personaId?: string, returnTo?: string) => {
    if (authConfig.devPersonas && personaId) rememberPersona(personaId);
    if (returnTo) rememberReturnTo(returnTo);
    await getUserManager().signinRedirect();
  }, []);

  const signOut = useCallback(async () => {
    await getUserManager().removeUser();
    apply(null);
  }, [apply]);

  const value = useMemo(() => ({ status, principal, signIn, signOut }), [status, principal, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
