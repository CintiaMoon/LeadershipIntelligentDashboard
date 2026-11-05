import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, configureApi, forgetCsrfToken } from './client';
import { ApiError, describeError } from './errors';
import { identityApi } from './identity';

const ORIGIN = 'http://localhost:3000/api/v1';
const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => {
  server.resetHandlers();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
});
afterAll(() => server.close());

let unauthorized = 0;
beforeEach(() => {
  unauthorized = 0;
  forgetCsrfToken();
  configureApi({ getToken: () => null, onUnauthorized: () => (unauthorized += 1), getCompany: () => 'acme', csrf: true });
});

describe('the session client', () => {
  it('names the company on every call and sends no bearer token', async () => {
    let seen: Request | undefined;
    server.use(http.get(`${ORIGIN}/assessments`, ({ request }) => ((seen = request), HttpResponse.json({ items: [] }))));
    await apiFetch('/assessments');
    expect(seen?.headers.get('X-Company-Id')).toBe('acme');
    expect(seen?.headers.get('Authorization')).toBeNull();
  });

  it('leaves the company out of the calls that belong to no company', async () => {
    let seen: Request | undefined;
    server.use(http.get(`${ORIGIN}/companies`, ({ request }) => ((seen = request), HttpResponse.json([]))));
    await identityApi.companies();
    expect(seen?.headers.get('X-Company-Id')).toBeNull();
  });

  it('fetches the CSRF token once and sends it on state-changing calls only', async () => {
    let csrfCalls = 0;
    const seen: Array<string | null> = [];
    server.use(
      http.get(`${ORIGIN}/auth/csrf`, () => {
        csrfCalls += 1;
        return HttpResponse.json({ headerName: 'X-XSRF-TOKEN', token: 'token-1' });
      }),
      http.post(`${ORIGIN}/members`, ({ request }) => (seen.push(request.headers.get('X-XSRF-TOKEN')), HttpResponse.json({ id: 'm' }, { status: 201 }))),
      http.get(`${ORIGIN}/members`, ({ request }) => (seen.push(request.headers.get('X-XSRF-TOKEN')), HttpResponse.json([]))),
    );
    await apiFetch('/members');
    expect(csrfCalls).toBe(0);
    await apiFetch('/members', { method: 'POST', body: {} });
    await apiFetch('/members', { method: 'POST', body: {} });
    expect(csrfCalls).toBe(1);
    expect(seen).toEqual([null, 'token-1', 'token-1']);
  });

  it('prefers the cookie the server keeps current', async () => {
    document.cookie = 'XSRF-TOKEN=from-cookie; path=/';
    let seen: string | null = null;
    server.use(http.post(`${ORIGIN}/members`, ({ request }) => ((seen = request.headers.get('X-XSRF-TOKEN')), HttpResponse.json({}, { status: 201 }))));
    await apiFetch('/members', { method: 'POST', body: {} });
    expect(seen).toBe('from-cookie');
  });

  it('replaces a stale CSRF token once and repeats the call', async () => {
    const tokens: Array<string | null> = [];
    let issued = 0;
    server.use(
      http.get(`${ORIGIN}/auth/csrf`, () => HttpResponse.json({ headerName: 'X-XSRF-TOKEN', token: `fresh-${(issued += 1)}` })),
      http.post(`${ORIGIN}/members`, ({ request }) => {
        const token = request.headers.get('X-XSRF-TOKEN');
        tokens.push(token);
        return token === 'fresh-2'
          ? HttpResponse.json({ id: 'm' }, { status: 201 })
          : HttpResponse.json({ code: 'CSRF_TOKEN_INVALID', message: 'no' }, { status: 403 });
      }),
    );
    await expect(apiFetch('/members', { method: 'POST', body: {} })).resolves.toEqual({ id: 'm' });
    expect(tokens).toEqual(['fresh-1', 'fresh-2']);
  });

  it('does not repeat a call that was refused for another reason', async () => {
    let calls = 0;
    server.use(
      http.get(`${ORIGIN}/auth/csrf`, () => HttpResponse.json({ headerName: 'X-XSRF-TOKEN', token: 't' })),
      http.post(`${ORIGIN}/members`, () => ((calls += 1), HttpResponse.json({ code: 'MEMBER_ACTION_REFUSED', message: 'no' }, { status: 403 }))),
    );
    await expect(apiFetch('/members', { method: 'POST', body: {} })).rejects.toMatchObject({ status: 403, code: 'MEMBER_ACTION_REFUSED' });
    expect(calls).toBe(1);
  });

  it('treats a 401 as the end of the session, except for the calls made before there is one', async () => {
    server.use(
      http.get(`${ORIGIN}/assessments`, () => HttpResponse.json({ code: 'UNAUTHENTICATED' }, { status: 401 })),
      http.get(`${ORIGIN}/auth/csrf`, () => HttpResponse.json({ headerName: 'X-XSRF-TOKEN', token: 't' })),
      http.post(`${ORIGIN}/auth/login`, () => HttpResponse.json({ code: 'AUTHENTICATION_FAILED', message: 'x' }, { status: 401 })),
    );
    await expect(apiFetch('/assessments')).rejects.toMatchObject({ status: 401 });
    expect(unauthorized).toBe(1);
    await expect(identityApi.login('a@example.com', 'wrong password here')).rejects.toBeInstanceOf(ApiError);
    expect(unauthorized).toBe(1);
  });

  it('keeps the wait a throttle asks for', async () => {
    server.use(
      http.get(`${ORIGIN}/auth/csrf`, () => HttpResponse.json({ headerName: 'X-XSRF-TOKEN', token: 't' })),
      http.post(`${ORIGIN}/auth/login`, () => HttpResponse.json({ code: 'TOO_MANY_REQUESTS', message: 'x' }, { status: 429, headers: { 'Retry-After': '90' } })),
    );
    const error = await identityApi.login('a@example.com', 'a long enough password').catch((e: unknown) => e);
    expect(error).toMatchObject({ status: 429, retryAfter: 90 });
    expect(describeError(error).message).toContain('2 minutes');
  });
});

describe('the bearer client', () => {
  it('is unchanged: a bearer token, no CSRF token and no company', async () => {
    configureApi({ getToken: () => 'tok', onUnauthorized: vi.fn() });
    let seen: Request | undefined;
    server.use(http.post(`${ORIGIN}/calculate`, ({ request }) => ((seen = request), HttpResponse.json({}))));
    await apiFetch('/calculate', { method: 'POST', body: {} });
    expect(seen?.headers.get('Authorization')).toBe('Bearer tok');
    expect(seen?.headers.get('X-XSRF-TOKEN')).toBeNull();
    expect(seen?.headers.get('X-Company-Id')).toBeNull();
  });
});
