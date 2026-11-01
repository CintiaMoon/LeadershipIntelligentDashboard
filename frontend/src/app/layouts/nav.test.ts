import { describe, expect, it } from 'vitest';
import type { Principal, Role } from '@/services/auth/principal';
import { navItems } from './nav';

const tenant = (...roles: Role[]): Principal => ({ subject: 'u', tenantId: 't', organizations: ['org-1'], roles });
const labels = (p: Principal | null) => navItems(p).map((i) => i.label);

describe('navItems', () => {
  it('gives owners and administrators the dashboard, the comparison and everything that manages assessments', () => {
    for (const role of ['OWNER', 'ADMIN'] as const) {
      expect(labels(tenant(role))).toEqual(['Dashboard', 'Compare', 'Questionnaires', 'Assessment runs', 'People']);
    }
  });

  it('gives every other tenant role only the dashboard and the comparison', () => {
    for (const role of ['ENGINEERING_LEADER', 'ANALYST', 'VIEWER'] as const) {
      expect(labels(tenant(role))).toEqual(['Dashboard', 'Compare']);
    }
  });

  it('gives the platform administrator the tenants and nothing tenant-scoped', () => {
    expect(labels({ subject: 'root', tenantId: '', organizations: [], roles: [], platformAdmin: true })).toEqual(['Tenants']);
  });

  it('gives a respondent their own assessments and nothing else', () => {
    expect(labels({ subject: 'r', tenantId: 't', organizations: [], roles: [], respondent: true })).toEqual(['My assessments']);
  });

  it('points every entry at a route that exists and never repeats one', () => {
    const all = [tenant('ADMIN'), { subject: 'root', tenantId: '', organizations: [], roles: [], platformAdmin: true }, { subject: 'r', tenantId: 't', organizations: [], roles: [], respondent: true }].flatMap(
      (p) => navItems(p).map((i) => i.to),
    );
    expect(all.every((to) => to.startsWith('/'))).toBe(true);
    expect(new Set(all).size).toBe(all.length);
  });

  it('shows nothing for nobody', () => {
    expect(labels(null)).toEqual(['Dashboard', 'Compare']);
  });
});
