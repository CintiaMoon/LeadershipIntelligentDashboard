import type { ReactNode } from 'react';
import { companyFromPath } from '@/app/router/company-path';
import { SessionAuthProvider } from './SessionAuthProvider';

/** Signed in with the application's own accounts (decisions D65 to D69); the company is the one in the address, if any. */
export function AuthProvider({ children }: { children: ReactNode }) {
  return <SessionAuthProvider tenantId={companyFromPath(window.location.pathname)}>{children}</SessionAuthProvider>;
}
