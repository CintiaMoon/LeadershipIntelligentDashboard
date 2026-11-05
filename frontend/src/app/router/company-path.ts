/**
 * The company is part of the address: every company screen lives under {@code /c/<tenantId>} (decision D69). The router for those
 * screens is built with that prefix as its basename, so the screens keep their own short paths, and switching company is a full
 * page load, which also forgets everything the previous company's session had cached.
 */
const COMPANY_PATH = /^\/c\/([^/]+)(?:\/|$)/;

/** The company named in an address, or null for an address outside any company. */
export function companyFromPath(pathname: string): string | null {
  const match = COMPANY_PATH.exec(pathname);
  if (!match) return null;
  try {
    return decodeURIComponent(match[1] as string);
  } catch {
    return null;
  }
}

/** The prefix of a company's screens, e.g. {@code /c/acme}. */
export function companyBase(tenantId: string): string {
  return `/c/${encodeURIComponent(tenantId)}`;
}

/** The address of a screen of a company, e.g. {@code /c/acme/dashboard}. */
export function companyUrl(tenantId: string, path = '/'): string {
  return `${companyBase(tenantId)}${path === '/' ? '/' : path.startsWith('/') ? path : `/${path}`}`;
}
