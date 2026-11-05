import type { NavigateFunction } from 'react-router-dom';
import { companyFromPath } from '@/app/router/company-path';

/** A place to return to after signing in: only an address on this site (never another origin or a protocol-relative one). */
export function safeReturn(raw: string | null | undefined): string | null {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//') || raw.startsWith('/\\')) return null;
  return raw;
}

/**
 * Goes to an address. The screens of a company are served by their own router (its addresses start with /c/<company>), so reaching
 * one from outside it is a full page load; anything else is an ordinary navigation.
 */
export function goTo(navigate: NavigateFunction, path: string): void {
  if (companyFromPath(path)) window.location.assign(path);
  else navigate(path, { replace: true });
}
