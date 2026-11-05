import { createContext, useContext } from 'react';
import type { Principal } from '@/services/auth/principal';
import type { Account, Company } from '@/types/identity';

export type AuthStatus = 'loading' | 'anonymous' | 'authenticated';

export interface AuthContextValue {
  status: AuthStatus;
  /** What the user may do in the company they are looking at (or as platform administrator). Null outside a company. */
  principal: Principal | null;
  signIn: (personaId?: string, returnTo?: string) => Promise<void>;
  signOut: () => Promise<void>;
  account?: Account | null;
  /** The companies the user may open (ACTIVE memberships only). */
  companies?: Company[];
  /** The company named in the address, if the user may open it. */
  company?: Company | null;
  /** Reads the account and the companies again, e.g. after signing in or accepting an invitation. */
  refresh?: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
