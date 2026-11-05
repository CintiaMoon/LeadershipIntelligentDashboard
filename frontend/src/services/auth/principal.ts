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
/** Reading the scoring configuration and how it came to be (decisions D52, D58): the platform administrator, owners, administrators and analysts. */
export const canReadConfiguration = (p: Principal | null) => isPlatformAdmin(p) || hasAny(p, ['OWNER', 'ADMIN', 'ANALYST']);
/** Changing it: the platform administrator for the platform default, owners and administrators for their tenant. The backend decides. */
export const canChangeConfiguration = (p: Principal | null) => isPlatformAdmin(p) || hasAny(p, ['OWNER', 'ADMIN']);


/**
 * What a signed-in account may do in one company, for the interface only (the server decides, from the membership, on every call).
 * The platform administrator belongs to no company.
 */
export function principalFor(account: { id: string; platformAdmin: boolean }, company: { id: string; roles: string[]; organizationIds: string[] } | null): Principal | null {
  if (account.platformAdmin) return { subject: account.id, tenantId: '', organizations: [], roles: [], platformAdmin: true };
  if (!company) return null;
  return {
    subject: account.id,
    tenantId: company.id,
    organizations: company.organizationIds,
    roles: company.roles.filter((r): r is Role => (ROLES as readonly string[]).includes(r)),
    respondent: company.roles.includes(RESPONDENT),
  };
}
