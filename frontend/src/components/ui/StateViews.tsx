import type { ReactNode } from 'react';
import { describeError } from '@/services/api/errors';
import { Button } from './Button';
import { Skeleton } from './Skeleton';

export function LoadingState({ label = 'Loading' }: { label?: string }) {
  return (
    <div role="status" aria-live="polite" className="space-y-3">
      <span className="sr-only">{label}</span>
      <Skeleton className="h-8 w-1/3" />
      <Skeleton className="h-32 w-full" />
      <Skeleton className="h-32 w-full" />
    </div>
  );
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="rounded-xl border border-dashed border-line bg-surface px-6 py-12 text-center">
      <h2 className="text-base font-semibold text-ink">{title}</h2>
      {description && <p className="mx-auto mt-1 max-w-md text-sm text-ink-soft">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const view = describeError(error);
  return (
    <div role="alert" className="rounded-xl border border-risk-critical/30 bg-risk-critical-soft px-6 py-8">
      <h2 className="text-base font-semibold text-risk-critical">{view.title}</h2>
      <p className="mt-1 text-sm text-ink-soft">{view.message}</p>
      {view.correlationId && <p className="mt-2 font-mono text-xs text-ink-faint">Reference: {view.correlationId}</p>}
      {view.retryable && onRetry && (
        <Button className="mt-4" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}
