import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { apiFetch, configureApi } from './client';
import { ApiError } from './errors';
import { api } from './endpoints';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('apiFetch', () => {
  it('sends the bearer token and builds the query string without empty values', async () => {
    configureApi({ getToken: () => 'tok-123', onUnauthorized: () => undefined });
    let seen: Request | undefined;
    server.use(
      http.get('http://localhost:3000/api/v1/scoring-runs', ({ request }) => {
        seen = request;
        return HttpResponse.json({ items: [] });
      }),
    );
    await api.runs({ organizationId: 'org-1', assessmentId: undefined, from: '', limit: 5 });
    expect(seen?.headers.get('Authorization')).toBe('Bearer tok-123');
    expect(new URL(seen?.url ?? '').search).toBe('?organizationId=org-1&limit=5');
  });

  it('posts JSON with an idempotency key when given', async () => {
    configureApi({ getToken: () => null, onUnauthorized: () => undefined });
    let key: string | null = null;
    let body: unknown;
    server.use(
      http.post('http://localhost:3000/api/v1/assessments/asm-1/score', async ({ request }) => {
        key = request.headers.get('Idempotency-Key');
        body = await request.text();
        return HttpResponse.json({ scoringRunId: 'r1' });
      }),
    );
    await api.score('asm-1', 'key-1');
    expect(key).toBe('key-1');
    expect(body).toBe('');
  });

  it('turns an error body into an ApiError with the correlation id', async () => {
    configureApi({ getToken: () => null, onUnauthorized: () => undefined });
    server.use(
      http.get('http://localhost:3000/api/v1/scoring-runs/missing', () =>
        HttpResponse.json({ code: 'SCORING_RUN_NOT_FOUND', message: 'not found', correlationId: 'c-9' }, { status: 404 }),
      ),
    );
    const error = await api.run('missing').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 404, code: 'SCORING_RUN_NOT_FOUND', correlationId: 'c-9' });
  });

  it('falls back to the correlation header and a generic code when the body is not JSON', async () => {
    configureApi({ getToken: () => null, onUnauthorized: () => undefined });
    server.use(
      http.get('http://localhost:3000/api/v1/x', () => new HttpResponse('<html>bad gateway</html>', { status: 502, headers: { 'X-Correlation-Id': 'h-1' } })),
    );
    const error = await apiFetch('/x').catch((e: unknown) => e);
    expect(error).toMatchObject({ status: 502, code: 'UNKNOWN', correlationId: 'h-1' });
  });

  it('signs the user out on 401', async () => {
    const onUnauthorized = vi.fn();
    configureApi({ getToken: () => 'expired', onUnauthorized });
    server.use(http.get('http://localhost:3000/api/v1/scoring-runs', () => HttpResponse.json({ code: 'UNAUTHENTICATED', message: 'x' }, { status: 401 })));
    await expect(api.runs()).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('encodes path segments', async () => {
    configureApi({ getToken: () => null, onUnauthorized: () => undefined });
    let path = '';
    server.use(
      http.get('http://localhost:3000/api/v1/assessments/:id', ({ request }) => {
        path = new URL(request.url).pathname;
        return HttpResponse.json({});
      }),
    );
    await api.assessment('a/b c');
    expect(path).toBe('/api/v1/assessments/a%2Fb%20c');
  });
});
