import { PenaltyCard } from '@/components/penalties/PenaltyCard';
import { Card, CardHeader } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/StateViews';
import type { Penalty } from '@/types/views';
import { formatScore } from '@/utils/presentation';
import type { Names } from './display';
import { groupPenalties } from './selectors';

interface Props {
  penalties: Penalty[];
  /** effective deduction per target, exactly as the backend aggregated it */
  effectiveByTarget: Record<string, number>;
  names: Names;
  runId?: string;
}

const SECTIONS = [
  { state: 'APPLIED', title: 'Applied', note: 'These deductions changed the adjusted scores.' },
  { state: 'SUPPRESSED', title: 'Suppressed', note: 'Requested, but another rule took precedence. They did not change any score.' },
  { state: 'INELIGIBLE', title: 'Ineligible', note: 'The triggering pattern did not qualify.' },
] as const;

const TILES = [...SECTIONS, { state: 'NOT_TRIGGERED' as const, title: 'Not triggered', note: '' }];

export function PenaltyOverview({ penalties, effectiveByTarget, names, runId }: Props) {
  const groups = groupPenalties(penalties);
  const totals = Object.entries(effectiveByTarget).filter(([, v]) => v > 0);
  return (
    <section aria-labelledby="penalties-heading">
      <Card>
        <CardHeader title="Penalties" description="A pattern is an organizational condition; a penalty is its scoring consequence." />
        <span id="penalties-heading" className="sr-only">
          Penalties
        </span>
        <div className="space-y-5 p-5">
          <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {TILES.map((s) => (
              <div key={s.state} className="rounded-lg bg-canvas px-3 py-2">
                <dt className="text-xs text-ink-soft">{s.title}</dt>
                <dd className="text-xl font-semibold tabular-nums">{groups[s.state].length}</dd>
              </div>
            ))}
          </dl>
          {totals.length > 0 && (
            <p className="text-sm text-ink-soft">
              Effective deductions:{' '}
              {totals.map(([target, value], i) => (
                <span key={target}>
                  {i > 0 && ', '}
                  {target === 'OVERALL' ? 'overall score' : names.dimension(target).name} <strong className="text-ink tabular-nums">{formatScore(value)}</strong>
                </span>
              ))}
            </p>
          )}
          {penalties.length === 0 && <EmptyState title="No penalties configured" description="This configuration defines no penalty rules." />}
          {SECTIONS.map((section) =>
            groups[section.state].length === 0 ? null : (
              <div key={section.state} role="group" aria-label={section.title}>
                <h3 className="text-sm font-semibold text-ink-soft">
                  {section.title} <span className="font-normal text-ink-faint">({groups[section.state].length})</span>
                </h3>
                <p className="mb-2 text-xs text-ink-faint">{section.note}</p>
                <div className="space-y-3">
                  {groups[section.state].map((p) => (
                    <PenaltyCard key={`${p.penaltyId}-${p.version}`} penalty={p} names={names} detailHref={runId ? `/runs/${runId}/penalties/${p.penaltyId}` : undefined} />
                  ))}
                </div>
              </div>
            ),
          )}
          {groups.NOT_TRIGGERED.length > 0 && (
            <details className="rounded-lg border border-line p-3">
              <summary className="cursor-pointer text-sm font-medium text-ink-soft">Not triggered ({groups.NOT_TRIGGERED.length})</summary>
              <div className="mt-3 space-y-3">
                {groups.NOT_TRIGGERED.map((p) => (
                  <PenaltyCard key={`${p.penaltyId}-${p.version}`} penalty={p} names={names} detailHref={runId ? `/runs/${runId}/penalties/${p.penaltyId}` : undefined} />
                ))}
              </div>
            </details>
          )}
        </div>
      </Card>
    </section>
  );
}
