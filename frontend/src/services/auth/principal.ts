export const ROLES = ['OWNER', 'ADMIN', 'ENGINEERING_LEADER', 'ANALYST', 'VIEWER'] as const;
export type Role = (typeof ROLES)[number];

/**
 * What the access token says about the user. The token is decoded only to shape the interface (organization list,
 * which buttons to show). The backend verifies the token and enforces every permission; nothing here is a security check.
 */
export interface Principal {
  subject: string;
  /** Empty for the platform administrator, who belongs to no tenant (decision D46). */
  tenantId: string;
  organizations: string[];
  roles: Role[];
  /** The platform administrator creates and lists tenants and sees no tenant data. */
  platformAdmin?: boolean;
  /** A respondent answers the questionnaires assigned to them and reaches nothing else (decision D48). */
  respondent?: boolean;
}

/** The role claim that marks the platform administrator. It is not one of the tenant roles. */
export const PLATFORM_ADMIN = 'PLATFORM_ADMIN';
/** The role claim of a person who answers questionnaires. It is not one of the tenant roles. */
export const RESPONDENT = 'RESPONDENT';

function base64UrlDecode(part: string): string {
  const padded = part.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(part.length / 4) * 4, '=');
  const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

export function decodePrincipal(accessToken: string | null | undefined): Principal | null {
  if (!accessToken) return null;
  try {
    const payload = JSON.parse(base64UrlDecode(accessToken.split('.')[1] ?? '')) as Record<string, unknown>;
    const claimedTenant = payload['tenant_id'];
    const tenantId = typeof claimedTenant === 'string' ? claimedTenant : '';
    const platformAdmin = Array.isArray(payload['roles']) && payload['roles'].includes(PLATFORM_ADMIN);
    // a token with neither a tenant nor the platform role is not one this interface can use
    if (tenantId === '' && !platformAdmin) return null;
    const orgs = Array.isArray(payload['org_ids']) ? payload['org_ids'].filter((o): o is string => typeof o === 'string') : [];
    const roles = Array.isArray(payload['roles'])
      ? payload['roles'].filter((r): r is Role => typeof r === 'string' && (ROLES as readonly string[]).includes(r))
      : [];
    const respondent = Array.isArray(payload['roles']) && payload['roles'].includes(RESPONDENT);
    return { subject: typeof payload['sub'] === 'string' ? payload['sub'] : '', tenantId, organizations: orgs, roles, platformAdmin, respondent };
  } catch {
    return null;
  }
}

function hasAny(principal: Principal | null, allowed: readonly Role[]): boolean {
  return principal !== null && principal.roles.some((r) => allowed.includes(r));
}

/** Decision D19: who may submit and score assessments. */
export const canScore = (p: Principal | null) => hasAny(p, ['OWNER', 'ADMIN', 'ENGINEERING_LEADER']);
/** Decision D19: who may recalculate with an explicit configuration version. */
export const canChooseConfiguration = (p: Principal | null) => hasAny(p, ['OWNER', 'ADMIN']);
/** Decision D19: who sees per-question evidence; everyone else receives a redacted copy. */
export const canSeeEvidence = (p: Principal | null) => hasAny(p, ['OWNER', 'ADMIN', 'ANALYST']);
/** Metrics and operational endpoints. */
export const isOperator = (p: Principal | null) => hasAny(p, ['OWNER', 'ADMIN']);
/** Creating and listing tenants. The backend decides; this only shapes the navigation. */
export const isPlatformAdmin = (p: Principal | null) => p?.platformAdmin === true;
/** Questionnaires, the roster and assessment runs (decisions D47, D48). The backend decides; this only shapes the navigation. */
export const canManageAssessments = (p: Principal | null) => hasAny(p, ['OWNER', 'ADMIN']);
/** Answering questionnaires. */
export const isRespondent = (p: Principal | null) => p?.respondent === true;
