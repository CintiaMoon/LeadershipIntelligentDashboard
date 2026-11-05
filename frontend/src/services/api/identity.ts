import type {
  Account,
  ApiToken,
  Company,
  CreateApiTokenRequest,
  CreatedApiToken,
  InvitationPreview,
  InviteMemberRequest,
  Member,
  SecurityAuditParams,
  SecurityEventPage,
  UpdateMemberRequest,
} from '@/types/identity';
import { apiFetch, forgetCsrfToken } from './client';
import { ApiError, describeError, waitText } from './errors';

const enc = encodeURIComponent;

/**
 * The account, company, member, API token and security audit endpoints (decisions D65 to D74). The calls made before there is a session
 * are {@code anonymous}: a 401 is their answer, not the end of a session.
 */
export const identityApi = {
  // ---------------- accounts and sessions ----------------
  me: (signal?: AbortSignal) => apiFetch<Account>('/auth/me', { signal, anonymous: true, noCompany: true }),
  login: async (email: string, password: string) => {
    const account = await apiFetch<Account>('/auth/login', { method: 'POST', body: { email, password }, anonymous: true, noCompany: true });
    forgetCsrfToken(); // a new session comes with a new CSRF token
    return account;
  },
  logout: async () => {
    await apiFetch<void>('/auth/logout', { method: 'POST', anonymous: true, noCompany: true });
    forgetCsrfToken();
  },
  register: (email: string, password: string, displayName: string) =>
    apiFetch<void>('/auth/register', { method: 'POST', body: { email, password, displayName }, anonymous: true, noCompany: true }),
  verifyEmail: (token: string, password: string) =>
    apiFetch<void>('/auth/verify-email', { method: 'POST', body: { token, password }, anonymous: true, noCompany: true }),
  resendVerification: (email: string) =>
    apiFetch<void>('/auth/resend-verification', { method: 'POST', body: { email }, anonymous: true, noCompany: true }),
  forgotPassword: (email: string) => apiFetch<void>('/auth/password/forgot', { method: 'POST', body: { email }, anonymous: true, noCompany: true }),
  resetPassword: (token: string, newPassword: string) =>
    apiFetch<void>('/auth/password/reset', { method: 'POST', body: { token, newPassword }, anonymous: true, noCompany: true }),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiFetch<void>('/auth/password/change', { method: 'POST', body: { currentPassword, newPassword }, noCompany: true }),

  // ---------------- invitations ----------------
  previewInvitation: (token: string) =>
    apiFetch<InvitationPreview>('/auth/invitations/preview', { method: 'POST', body: { token }, anonymous: true, noCompany: true }),
  registerWithInvitation: (token: string, password: string, displayName: string) =>
    apiFetch<void>('/auth/invitations/register', { method: 'POST', body: { token, password, displayName }, anonymous: true, noCompany: true }),
  acceptInvitation: (token: string) => apiFetch<void>('/auth/invitations/accept', { method: 'POST', body: { token }, noCompany: true }),

  // ---------------- companies ----------------
  companies: (signal?: AbortSignal) => apiFetch<Company[]>('/companies', { signal, noCompany: true }),

  // ---------------- members (decision D72) ----------------
  members: (signal?: AbortSignal) => apiFetch<Member[]>('/members', { signal }),
  inviteMember: (request: InviteMemberRequest) => apiFetch<Member>('/members', { method: 'POST', body: request }),
  updateMember: (id: string, request: UpdateMemberRequest) => apiFetch<Member>(`/members/${enc(id)}`, { method: 'PATCH', body: request }),
  removeMember: (id: string) => apiFetch<void>(`/members/${enc(id)}`, { method: 'DELETE' }),
  resendInvitation: (id: string) => apiFetch<void>(`/members/${enc(id)}/resend-invitation`, { method: 'POST' }),
  invitePerson: (personId: string) => apiFetch<Member>(`/people/${enc(personId)}/invite`, { method: 'POST' }),

  // ---------------- API tokens (decision D70) ----------------
  apiTokens: (signal?: AbortSignal) => apiFetch<ApiToken[]>('/api-tokens', { signal }),
  createApiToken: (request: CreateApiTokenRequest) => apiFetch<CreatedApiToken>('/api-tokens', { method: 'POST', body: request }),
  revokeApiToken: (id: string) => apiFetch<void>(`/api-tokens/${enc(id)}`, { method: 'DELETE' }),

  // ---------------- security audit (decision D74) ----------------
  securityAudit: (params: SecurityAuditParams, signal?: AbortSignal) => apiFetch<SecurityEventPage>('/security-audit', { query: { ...params }, signal }),
  platformSecurityAudit: (params: SecurityAuditParams, signal?: AbortSignal) =>
    apiFetch<SecurityEventPage>('/platform/security-audit', { query: { ...params }, signal, noCompany: true }),
};

/**
 * What a person is told when an account call fails. Account calls say less than the rest of the interface on purpose: a wrong email
 * or password, an unknown address and a pending account all read the same, because the server answers them the same.
 */
export function describeAuthError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'AUTHENTICATION_FAILED':
        return 'The email or the password is not correct.';
      case 'WEAK_PASSWORD':
        return error.message || 'Choose a stronger password: 12 to 128 characters that are not easy to guess.';
      case 'INVALID_TOKEN':
        return 'This link is not valid, has already been used, or has expired.';
      case 'INVALID_ACCOUNT_REQUEST':
        return error.message || 'Check the details and try again.';
      case 'TOO_MANY_REQUESTS':
        return error.retryAfter ? `Too many attempts. Try again in ${waitText(error.retryAfter)}.` : 'Too many attempts. Try again in a little while.';
      default:
        break;
    }
  }
  const view = describeError(error);
  return `${view.title}. ${view.message}`;
}
