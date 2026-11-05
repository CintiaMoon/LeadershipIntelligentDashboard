import { describe, expect, it } from 'vitest';
import type { Principal, Role } from '@/services/auth/principal';
import { navItems } from './nav';

const tenant = (...roles: Role[]): Principal => ({ subject: 'u', tenantId: 't', organizations: ['org-1'], roles });
const labels = (p: Principal | null) => navItems(p).map((i) => i.label);

describe('navItems', () => {
  it('gives owners and administrators the dashboard, the comparison and everything that manages assessments', () => {
    for (const role of ['OWNER', 'ADMIN'] as const) {
      expect(labels(tenant(role))).toEqual(['Dashboard', 'Compare', 'Reports', 'Questionnaires', 'Assessment runs', 'People', 'Configuration', 'Members', 'API tokens', 'Security audit']);
    }
  });

  it('gives analysts the configuration to read, and the other roles only the dashboard and the comparison', () => {
    expect(labels(tenant('ANALYST'))).toEqual(['Dashboard', 'Compare', 'Reports', 'Configuration']);
    for (const role of ['ENGINEERING_LEADER', 'VIEWER'] as const) {
      expect(labels(tenant(role))).toEqual(['Dashboard', 'Compare', 'Reports']);
    }
  });

  it('gives the platform administrator the tenants and the platform configuration, nothing tenant-scoped', () => {
    expect(labels({ subject: 'root', tenantId: '', organizations: [], roles: [], platformAdmin: true })).toEqual(['Tenants', 'Configuration', 'Security audit']);
  });

  it('gives a respondent their own assessments and nothing else', () => {
    expect(labels({ subject: 'r', tenantId: 't', organizations: [], roles: [], respondent: true })).toEqual(['My assessments']);
  });

  it('points every entry at a route that exists and never repeats one for a role', () => {
    const roles = [tenant('ADMIN'), { subject: 'root', tenantId: '', organizations: [], roles: [], platformAdmin: true }, { subject: 'r', tenantId: 't', organizations: [], roles: [], respondent: true }];
    for (const p of roles) {
      const to = navItems(p).map((i) => i.to);
      expect(to.every((t) => t.startsWith('/'))).toBe(true);
      expect(new Set(to).size).toBe(to.length);
    }
  });

  it('shows nothing for nobody', () => {
    expect(labels(null)).toEqual(['Dashboard', 'Compare', 'Reports']);
  });
});
