import { beforeEach, describe, expect, it } from 'vitest';
import { rememberReturnTo, takeReturnTo } from './oidc';

beforeEach(() => window.sessionStorage.clear());

describe('where a user goes after signing in', () => {
  it('goes to the home route when nothing was remembered, which then picks the right start page for the role', () => {
    expect(takeReturnTo()).toBe('/');
  });

  it('returns to the page that asked for the sign-in, once', () => {
    rememberReturnTo('/runs/abc/explanation?x=1');
    expect(takeReturnTo()).toBe('/runs/abc/explanation?x=1');
    expect(takeReturnTo()).toBe('/');
  });

  it.each([['https://evil.example/'], ['//evil.example/'], ['dashboard'], ['javascript:alert(1)']])('ignores %s, which is not a path of this application', (target) => {
    rememberReturnTo(target);
    expect(takeReturnTo()).toBe('/');
  });
});
