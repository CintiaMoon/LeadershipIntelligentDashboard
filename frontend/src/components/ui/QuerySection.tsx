import type { ReactNode } from 'react';
import { Card } from './Card';
import { ErrorState } from './StateViews';
import { Skeleton } from './Skeleton';

interface QueryLike {
  isPending: boolean;
  isError: boolean;
  error: unknown;
  refetch: () => unknown;
}

/** One section of a page with its own loading and error state, so one failing request does not blank the whole page. */
export function QuerySection({ query, label, children }: { query: QueryLike; label: string; children: () => ReactNode }) {
  if (query.isPending) {
    return (
      <Card className="space-y-3 p-5" aria-busy="true">
        <span className="sr-only" role="status">
          Loading {label}
        </span>
        <Skeleton className="h-6 w-1/4" />
        <Skeleton className="h-24 w-full" />
      </Card>
    );
  }
  if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  return <>{children()}</>;
}
