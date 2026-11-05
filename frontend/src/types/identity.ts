// Shapes of the account, company, member, API token and security audit endpoints (decisions D65 to D74). Written by hand against
// docs/implementation/API_CONTRACT.md sections 18 to 21.

export interface CompanyRef {
  id: string;
  name: string;
}

/** The signed-in user, from sign-in and from {@code GET /auth/me}. */
export interface Account {
  id: string;
  email: string;
  displayName: string;
  platformAdmin: boolean;
  companies: CompanyRef[];
}

/** A company the user may open, with the roles held there and the organizations reached ({@code GET /companies}). */
export interface Company {
  id: string;
  name: string;
  roles: string[];
  /** The organizations that exist and are reachable now. */
  organizationIds: string[];
  /** Not limited to a list: a new organization may still be named. */
  allOrganizations: boolean;
}

export type MemberStatus = 'INVITED' | 'ACTIVE' | 'SUSPENDED';

export interface Member {
  id: string;
  /** The invited address while the person has no account yet. */
  email: string;
  displayName?: string | null;
  status: MemberStatus;
  roles: string[];
  organizationIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface InviteMemberRequest {
  email: string;
  roles: string[];
  organizationIds?: string[];
}

export interface UpdateMemberRequest {
  roles?: string[];
  organizationIds?: string[];
  status?: 'ACTIVE' | 'SUSPENDED';
}

export interface InvitationPreview {
  companyName: string;
  email: string;
  roles: string[];
  accountExists: boolean;
}

export type ApiTokenStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED';

export interface ApiToken {
  id: string;
  name: string;
  role: string;
  /** The first characters of the secret, to tell tokens apart. */
  prefix: string;
  organizationIds: string[];
  createdBy: string;
  createdAt: string;
  expiresAt?: string | null;
  lastUsedAt?: string | null;
  revokedAt?: string | null;
  status: ApiTokenStatus;
}

export interface CreateApiTokenRequest {
  name: string;
  role: string;
  expiresAt?: string;
  organizationIds?: string[];
}

/** The answer to creating a token: the only time the secret is available. */
export interface CreatedApiToken {
  token: ApiToken;
  secret: string;
}

export interface SecurityEvent {
  id: number;
  occurredAt: string;
  eventType: string;
  outcome: 'SUCCESS' | 'FAILURE';
  userId?: string | null;
  userEmail?: string | null;
  tenantId?: string | null;
  clientIp?: string | null;
  userAgent?: string | null;
  detail: Record<string, string>;
}

export interface SecurityEventPage {
  items: SecurityEvent[];
  page: number;
  size: number;
  total: number;
}

export interface SecurityAuditParams {
  eventType?: string;
  outcome?: string;
  userId?: string;
  from?: string;
  to?: string;
  tenantId?: string;
  page?: number;
  size?: number;
}

export interface CsrfToken {
  headerName: string;
  token: string;
}

/** Every role a membership may hold, strongest first; RESPONDENT stands alone. */
export const MEMBER_ROLES = ['OWNER', 'ADMIN', 'ENGINEERING_LEADER', 'ANALYST', 'VIEWER', 'RESPONDENT'] as const;
/** The roles an API token may carry (never OWNER). */
export const TOKEN_ROLES = ['ADMIN', 'ENGINEERING_LEADER', 'ANALYST', 'VIEWER'] as const;
