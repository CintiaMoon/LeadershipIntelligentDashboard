import type { ApiErrorBody } from '@/types/api';
import { UnexpectedResponseError } from '@/types/views';

export type ErrorKind =
  | 'validation'
  | 'unauthenticated'
  | 'forbidden'
  | 'not-found'
  | 'conflict'
  | 'unprocessable'
  | 'rate-limited'
  | 'server'
  | 'unexpected'
  | 'unavailable'
  | 'network';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly correlationId?: string,
    public readonly details?: Record<string, string>,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  static fromBody(status: number, body: Partial<ApiErrorBody> | null, headerCorrelationId?: string | null): ApiError {
    return new ApiError(status, body?.code ?? 'UNKNOWN', body?.message ?? '', body?.correlationId ?? headerCorrelationId ?? undefined, body?.details);
  }
}

export interface ErrorView {
  kind: ErrorKind;
  title: string;
  message: string;
  retryable: boolean;
  correlationId?: string;
}

/** Central 400/401/403/404/409/422/429/500/503 handling (frontend-spec 16.3). Server faults never show backend wording. */
export function describeError(error: unknown): ErrorView {
  if (error instanceof UnexpectedResponseError) {
    return { kind: 'unexpected', title: 'The data arrived in an unexpected format', message: 'This is a fault on our side, not on your connection. Try again later and share the details with support if it continues.', retryable: false };
  }
  if (!(error instanceof ApiError)) {
    return { kind: 'network', title: 'Cannot reach the service', message: 'Check your connection and try again.', retryable: true };
  }
  const correlationId = error.correlationId;
  switch (error.status) {
    case 400:
      return { kind: 'validation', title: 'The request was not valid', message: error.message || 'Check the values and try again.', retryable: false, correlationId };
    case 401:
      return { kind: 'unauthenticated', title: 'Your session has ended', message: 'Sign in again to continue.', retryable: false, correlationId };
    case 403:
      return { kind: 'forbidden', title: 'You do not have access to this', message: 'Ask an administrator if you need access.', retryable: false, correlationId };
    case 404:
      return { kind: 'not-found', title: 'Not found', message: 'It may have been removed, or it belongs to another organization.', retryable: false, correlationId };
    case 409:
      return { kind: 'conflict', title: 'This conflicts with existing data', message: error.message || 'Reload and try again.', retryable: false, correlationId };
    case 422:
      return { kind: 'unprocessable', title: 'The data could not be scored', message: error.message || 'Review the answers and try again.', retryable: false, correlationId };
    case 429:
      return { kind: 'rate-limited', title: 'Too many requests', message: 'Wait a moment and try again.', retryable: true, correlationId };
    case 503:
      return { kind: 'unavailable', title: 'The service is temporarily unavailable', message: 'Try again shortly.', retryable: true, correlationId };
    default:
      return { kind: 'server', title: 'Something went wrong on our side', message: 'Try again. If it keeps happening, share the reference below with support.', retryable: error.status >= 500, correlationId };
  }
}

/** Whether TanStack Query should retry: only transient failures, and only a couple of times. */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  return failureCount < 2 && describeError(error).retryable;
}
