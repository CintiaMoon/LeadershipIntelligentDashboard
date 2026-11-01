import { SeverityBadge } from '@/components/patterns/SeverityBadge';
import { Card, CardHeader } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/StateViews';
import type { Recommendations } from '@/types/api';
import type { Names } from './display';

/** Recommended actions exactly as the backend resolved them from the configured catalog, each traced to its findings. */
export function RecommendationsPanel({ data, names }: { data: Recommendations; names: Names }) {
  const items = data.recommendations ?? [];
  const unmapped = data.unmappedActionKeys ?? [];
  return (
    <section aria-labelledby="recommendations-heading">
      <Card>
        <CardHeader title="Recommended actions" description="Prioritized by the severity of the findings behind them" />
        <span id="recommendations-heading" className="sr-only">
          Recommended actions
        </span>
        <div className="p-5">
          {items.length === 0 ? (
            <EmptyState title="No recommended actions" description="Actions appear when a detected pattern or imbalance has a configured recommendation." />
          ) : (
            <ol className="space-y-4">
              {items.map((r) => (
                <li key={r.key} className="rounded-lg border border-line p-4">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <h3 className="text-sm font-semibold">{r.title}</h3>
                    {r.severity && <SeverityBadge severity={r.severity} />}
                  </div>
                  <p className="mt-1 text-sm text-ink-soft">{r.description}</p>
                  <p className="mt-2 text-xs text-ink-faint">
                    Because of:{' '}
                    {(r.sources ?? []).map((s, i) => (
                      <span key={`${s.type}-${s.id}`}>
                        {i > 0 && ', '}
                        {s.type === 'PATTERN' ? (
                          <a className="underline hover:text-ink" href={`#pattern-${s.id}`}>
                            {names.pattern(s.id ?? '').name}
                          </a>
                        ) : (
                          <span>Imbalance {s.id}</span>
                        )}
                      </span>
                    ))}
                  </p>
                </li>
              ))}
            </ol>
          )}
          {unmapped.length > 0 && (
            <p className="mt-4 text-xs text-ink-faint">
              {unmapped.length} suggested {unmapped.length === 1 ? 'action has' : 'actions have'} no description in this configuration: {unmapped.join(', ')}.
            </p>
          )}
        </div>
      </Card>
    </section>
  );
}
