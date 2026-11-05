import { describe, expect, it } from 'vitest';
import { canChooseConfiguration, canManageAssessments, canScore, canSeeEvidence, isOperator, isPlatformAdmin, isRespondent, principalFor, type Principal } from './principal';

const principal = (...roles: Principal['roles']): Principal => ({ subject: 's', tenantId: 't', organizations: ['org-1'], roles });
const account = { id: 'u', platformAdmin: false };
const company = (roles: string[], organizationIds: string[] = ['org-1']) => ({ id: 't-1', roles, organizationIds });

describe('principalFor', () => {
  it('takes the company, the organizations and the known roles from the membership', () => {
    expect(principalFor(account, company(['ADMIN', 'WIZARD'], ['org-1', 'org-2']))).toEqual({
      subject: 'u',
      tenantId: 't-1',
      organizations: ['org-1', 'org-2'],
      roles: ['ADMIN'],
      respondent: false,
    });
  });

  it('is nothing without a company, unless the account is the platform administrator', () => {
    expect(principalFor(account, null)).toBeNull();
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
  it('belongs to no company and keeps no tenant role', () => {
    const p = principalFor({ id: 'root', platformAdmin: true }, null);
    expect(p).toEqual({ subject: 'root', tenantId: '', organizations: [], roles: [], platformAdmin: true });
    expect(isPlatformAdmin(p)).toBe(true);
    expect(canScore(p)).toBe(false);
    expect(canSeeEvidence(p)).toBe(false);
    expect(isOperator(p)).toBe(false);
  });

  it('does not become one through a company, however a role is named', () => {
    expect(isPlatformAdmin(principal('OWNER', 'ADMIN'))).toBe(false);
    expect(isPlatformAdmin(principalFor(account, company(['ADMIN', 'platform_admin', 'PLATFORM_ADMIN'])))).toBe(false);
    expect(isPlatformAdmin(null)).toBe(false);
  });

  it('is the platform administrator even when it is given a company, and then has no company', () => {
    expect(principalFor({ id: 'u', platformAdmin: true }, company(['VIEWER']))?.tenantId).toBe('');
  });
});

describe('the respondent (decision D48)', () => {
  it('is recognised by the role, keeps no tenant role and manages nothing', () => {
    const p = principalFor(account, company(['RESPONDENT'], []));
    expect(p).toMatchObject({ tenantId: 't-1', roles: [], respondent: true });
    expect(isRespondent(p)).toBe(true);
    expect(canManageAssessments(p)).toBe(false);
    expect(canScore(p)).toBe(false);
    expect(canSeeEvidence(p)).toBe(false);
  });

  it('is not granted by a tenant role, and is not a platform administrator', () => {
    expect(isRespondent(principal('OWNER', 'ADMIN'))).toBe(false);
    expect(isRespondent(principalFor(account, company(['ADMIN', 'respondent'])))).toBe(false);
    expect(isRespondent(null)).toBe(false);
  });

  it('lets only owners and administrators manage questionnaires, people and runs', () => {
    expect(canManageAssessments(principal('OWNER', 'OWNER'))).toBe(true);
    expect(canManageAssessments(principal('ADMIN', 'ADMIN'))).toBe(true);
    for (const role of ['ENGINEERING_LEADER', 'ANALYST', 'VIEWER'] as const) expect(canManageAssessments(principal(role, role))).toBe(false);
    expect(canManageAssessments(null)).toBe(false);
  });
});
