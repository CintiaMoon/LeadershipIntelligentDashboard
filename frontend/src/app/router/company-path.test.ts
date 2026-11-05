import { describe, expect, it } from 'vitest';
import { companyBase, companyFromPath, companyUrl } from './company-path';

describe('company-path', () => {
  it('reads the company out of an address', () => {
    expect(companyFromPath('/c/acme/dashboard')).toBe('acme');
    expect(companyFromPath('/c/acme')).toBe('acme');
    expect(companyFromPath('/c/acme/')).toBe('acme');
    expect(companyFromPath('/c/acme-corp/runs/1/explanation')).toBe('acme-corp');
  });

  it('knows an address that is outside any company', () => {
    expect(companyFromPath('/')).toBeNull();
    expect(companyFromPath('/login')).toBeNull();
    expect(companyFromPath('/companies')).toBeNull();
    expect(companyFromPath('/c')).toBeNull();
    expect(companyFromPath('/c/')).toBeNull();
    expect(companyFromPath('/x/c/acme')).toBeNull();
    expect(companyFromPath('/platform/tenants')).toBeNull();
  });

  it('survives an address that is not valid', () => {
    expect(companyFromPath('/c/%E0%A4%A/dashboard')).toBeNull();
  });

  it('builds the prefix and the address of a screen', () => {
    expect(companyBase('acme')).toBe('/c/acme');
    expect(companyUrl('acme')).toBe('/c/acme/');
    expect(companyUrl('acme', '/dashboard')).toBe('/c/acme/dashboard');
    expect(companyUrl('acme', 'members')).toBe('/c/acme/members');
    expect(companyFromPath(companyUrl('a b'))).toBe('a b');
  });
});
