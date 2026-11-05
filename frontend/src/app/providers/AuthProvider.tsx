import type { ReactNode } from 'react';
import { companyFromPath } from '@/app/router/company-path';
import { authConfig } from '@/services/auth/config';
import { OidcAuthProvider } from './OidcAuthProvider';
import { SessionAuthProvider } from './SessionAuthProvider';

/** Picks how people sign in: the application's own accounts, or (until the last phase of the identity work) the external provider. */
export function AuthProvider({ children }: { children: ReactNode }) {
  if (authConfig.mode === 'oidc') return <OidcAuthProvider>{children}</OidcAuthProvider>;
  return <SessionAuthProvider tenantId={companyFromPath(window.location.pathname)}>{children}</SessionAuthProvider>;
}
