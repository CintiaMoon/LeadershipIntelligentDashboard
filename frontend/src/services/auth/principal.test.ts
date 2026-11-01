import { describe, expect, it } from 'vitest';
import { fakeToken } from '@/tests/utils';
import { canChooseConfiguration, canManageAssessments, canScore, canSeeEvidence, decodePrincipal, isOperator, isPlatformAdmin, isRespondent, type Principal } from './principal';

const principal = (...roles: Principal['roles']): Principal => ({ subject: 's', tenantId: 't', organizations: ['org-1'], roles });

describe('decodePrincipal', () => {
  it('reads tenant, organizations and known roles from the token', () => {
    const p = decodePrincipal(fakeToken({ sub: 'u', tenant_id: 't-1', org_ids: ['org-1', 'org-2', 7], roles: ['ADMIN', 'WIZARD'] }));
    expect(p).toEqual({ subject: 'u', tenantId: 't-1', organizations: ['org-1', 'org-2'], roles: ['ADMIN'], platformAdmin: false, respondent: false });
  });

  it('returns null without a tenant, for garbage and for nothing', () => {
    expect(decodePrincipal(fakeToken({ sub: 'u' }))).toBeNull();
    expect(decodePrincipal('not-a-token')).toBeNull();
    expect(decodePrincipal(null)).toBeNull();
    expect(decodePrincipal('')).toBeNull();
  });
});

describe('role helpers follow decision D19', () => {
  it('lets leaders score but not choose a configuration', () => {
    expect(canScore(principal('ENGINEERING_LEADER'))).toBe(true);
    expect(canChooseConfiguration(principal('ENGINEERING_LEADER'))).toBe(false);
  });
  it('limits explicit configuration and operations to owners and admins', () => {
    expect(canChooseConfiguration(principal('ADMIN'))).toBe(true);
    expect(isOperator(principal('OWNER'))).toBe(true);
    expect(isOperator(principal('ANALYST'))).toBe(false);
  });
  it('shows evidence to owners, admins and analysts only', () => {
    expect(canSeeEvidence(principal('ANALYST'))).toBe(true);
    expect(canSeeEvidence(principal('VIEWER'))).toBe(false);
    expect(canSeeEvidence(null)).toBe(false);
    expect(canScore(principal('VIEWER'))).toBe(false);
  });
});

describe('the platform administrator (decision D46)', () => {
  it('is recognised without a tenant and keeps no tenant role', () => {
    const p = decodePrincipal(fakeToken({ sub: 'root', roles: ['PLATFORM_ADMIN'] }));
    expect(p).toEqual({ subject: 'root', tenantId: '', organizations: [], roles: [], platformAdmin: true, respondent: false });
    expect(isPlatformAdmin(p)).toBe(true);
    expect(canScore(p)).toBe(false);
    expect(canSeeEvidence(p)).toBe(false);
    expect(isOperator(p)).toBe(false);
  });

  it('is not granted by a tenant role, however it is named', () => {
    expect(isPlatformAdmin(principal('OWNER', 'ADMIN'))).toBe(false);
    expect(isPlatformAdmin(decodePrincipal(fakeToken({ sub: 'u', tenant_id: 't', roles: ['ADMIN', 'platform_admin'] })))).toBe(false);
    expect(isPlatformAdmin(null)).toBe(false);
  });

  it('may also carry a tenant, which changes nothing about what the role allows', () => {
    const p = decodePrincipal(fakeToken({ sub: 'u', tenant_id: 't', roles: ['PLATFORM_ADMIN', 'VIEWER'] }));
    expect(p?.platformAdmin).toBe(true);
    expect(p?.roles).toEqual(['VIEWER']);
  });

  it('is not a usable token without a tenant and without the role', () => {
    expect(decodePrincipal(fakeToken({ sub: 'u', roles: ['ADMIN'] }))).toBeNull();
  });
});

describe('the respondent (decision D48)', () => {
  it('is recognised by the role claim, keeps no tenant role and manages nothing', () => {
    const p = decodePrincipal(fakeToken({ sub: 'r', tenant_id: 't', roles: ['RESPONDENT'], email: 'r@acme.test' }));
    expect(p).toMatchObject({ tenantId: 't', roles: [], respondent: true, platformAdmin: false });
    expect(isRespondent(p)).toBe(true);
    expect(canManageAssessments(p)).toBe(false);
    expect(canScore(p)).toBe(false);
    expect(canSeeEvidence(p)).toBe(false);
  });

  it('is not granted by a tenant role, and is not a platform administrator', () => {
    expect(isRespondent(principal('OWNER', 'ADMIN'))).toBe(false);
    expect(isRespondent(decodePrincipal(fakeToken({ sub: 'u', tenant_id: 't', roles: ['ADMIN', 'respondent'] })))).toBe(false);
    expect(isRespondent(null)).toBe(false);
  });

  it('lets only owners and administrators manage questionnaires, people and runs', () => {
    expect(canManageAssessments(principal('OWNER', 'OWNER'))).toBe(true);
    expect(canManageAssessments(principal('ADMIN', 'ADMIN'))).toBe(true);
    for (const role of ['ENGINEERING_LEADER', 'ANALYST', 'VIEWER'] as const) expect(canManageAssessments(principal(role, role))).toBe(false);
    expect(canManageAssessments(null)).toBe(false);
  });
});
