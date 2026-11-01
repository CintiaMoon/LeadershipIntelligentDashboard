import { describe, expect, it } from 'vitest';
import { UnexpectedResponseError } from '@/types/views';
import { ApiError, describeError, shouldRetry } from './errors';

const err = (status: number, message = 'backend text') => new ApiError(status, 'CODE', message, 'corr-1');

describe('describeError', () => {
  it.each([
    [400, 'validation', false],
    [401, 'unauthenticated', false],
    [403, 'forbidden', false],
    [404, 'not-found', false],
    [409, 'conflict', false],
    [422, 'unprocessable', false],
    [429, 'rate-limited', true],
    [500, 'server', true],
    [503, 'unavailable', true],
  ])('maps %i to %s (retryable %s)', (status, kind, retryable) => {
    const view = describeError(err(status));
    expect(view.kind).toBe(kind);
    expect(view.retryable).toBe(retryable);
    expect(view.correlationId).toBe('corr-1');
  });

  it('never shows backend wording for server faults', () => {
    expect(describeError(err(500, 'NullPointerException at Foo.java:12')).message).not.toContain('NullPointer');
  });

  it('treats anything that is not an ApiError as a network problem', () => {
    const view = describeError(new TypeError('Failed to fetch'));
    expect(view.kind).toBe('network');
    expect(view.retryable).toBe(true);
  });

  it('does not blame the connection when a response has an unexpected shape', () => {
    const view = describeError(new UnexpectedResponseError('patterns'));
    expect(view.kind).toBe('unexpected');
    expect(view.title).not.toMatch(/reach/i);
    expect(view.retryable).toBe(false);
  });

  it('retries only transient failures, at most twice', () => {
    expect(shouldRetry(0, err(503))).toBe(true);
    expect(shouldRetry(2, err(503))).toBe(false);
    expect(shouldRetry(0, err(404))).toBe(false);
    expect(shouldRetry(0, err(422))).toBe(false);
  });
});
