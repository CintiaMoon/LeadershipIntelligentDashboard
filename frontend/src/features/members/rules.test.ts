import { describe, expect, it } from 'vitest';
import { mayGrantRole, mayManageRoles, parseList, toggleRole } from './rules';

describe('member rules for the interface', () => {
  it('lets an owner give every role', () => {
    for (const role of ['OWNER', 'ADMIN', 'ENGINEERING_LEADER', 'ANALYST', 'VIEWER', 'RESPONDENT']) expect(mayGrantRole(['OWNER'], role)).toBe(true);
  });

  it('keeps owner and administrator from an administrator', () => {
    expect(mayGrantRole(['ADMIN'], 'OWNER')).toBe(false);
    expect(mayGrantRole(['ADMIN'], 'ADMIN')).toBe(false);
    for (const role of ['ENGINEERING_LEADER', 'ANALYST', 'VIEWER', 'RESPONDENT']) expect(mayGrantRole(['ADMIN'], role)).toBe(true);
  });

  it('lets nobody else give anything', () => {
    expect(mayGrantRole(['ANALYST'], 'VIEWER')).toBe(false);
    expect(mayGrantRole([], 'VIEWER')).toBe(false);
  });

  it('manages a member only if every one of their roles could be given', () => {
    expect(mayManageRoles(['ADMIN'], ['VIEWER', 'ANALYST'])).toBe(true);
    expect(mayManageRoles(['ADMIN'], ['VIEWER', 'ADMIN'])).toBe(false);
    expect(mayManageRoles(['OWNER'], ['OWNER'])).toBe(true);
  });

  it('keeps a respondent on its own', () => {
    expect(toggleRole(['VIEWER'], 'RESPONDENT')).toEqual(['RESPONDENT']);
    expect(toggleRole(['RESPONDENT'], 'ANALYST')).toEqual(['ANALYST']);
    expect(toggleRole(['VIEWER'], 'ANALYST')).toEqual(['VIEWER', 'ANALYST']);
    expect(toggleRole(['VIEWER', 'ANALYST'], 'VIEWER')).toEqual(['ANALYST']);
  });

  it('reads a list typed with commas or spaces', () => {
    expect(parseList('org-1, org-2  org-3,,')).toEqual(['org-1', 'org-2', 'org-3']);
    expect(parseList('org-1, org-1')).toEqual(['org-1']);
    expect(parseList('  ')).toEqual([]);
  });
});
