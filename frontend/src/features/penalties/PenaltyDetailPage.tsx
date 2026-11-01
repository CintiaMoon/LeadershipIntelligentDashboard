import { Link, useParams } from 'react-router-dom';
import { SegmentTrack } from '@/components/explanations/StackedBar';
import { PenaltyStateBadge } from '@/components/penalties/PenaltyStateBadge';
import { Card, CardHeader } from '@/components/ui/Card';
import { BackLink, PageHeader } from '@/components/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateViews';
import { useRunDetails } from '@/features/details/useRunDetails';
import { penaltyReason } from '@/utils/labels';
import { formatScore, humanizeCode } from '@/utils/presentation';

/** Candidate (requested) and effective deductions side by side, with everything that decided the difference. */
export function PenaltyDetailPage() {
  const { runId, penaltyId } = useParams();
  const d = useRunDetails(runId);

  if (d.penalties.isPending || d.patterns.isPending) return <LoadingState label="Loading the penalty" />;
  if (d.penalties.isError) return <ErrorState error={d.penalties.error} onRetry={() => void d.penalties.refetch()} />;
  if (d.patterns.isError) return <ErrorState error={d.patterns.error} onRetry={() => void d.patterns.refetch()} />;
  if (d.parsed.error) return <ErrorState error={d.parsed.error} />;

  const { names, parsed } = d;
  const penalty = parsed.penalties.find((p) => p.penaltyId === penaltyId);
  if (!penalty) {
    return <EmptyState title="Penalty not found" description="This penalty is not part of the selected run." action={<BackLink to="/dashboard">Back to the dashboard</BackLink>} />;
  }

  const display = names.penalty(penalty.penaltyId, penalty.code);
  const trigger = parsed.patterns.find((p) => p.patternId === penalty.triggerPatternId);
  const triggerName = names.pattern(penalty.triggerPatternId, trigger?.code);
  const winner = penalty.winningPenaltyId ? parsed.penalties.find((p) => p.penaltyId === penalty.winningPenaltyId) : undefined;
  const peers = parsed.penalties.filter((p) => p.target === penalty.target && p.status !== 'NOT_TRIGGERED');
  const applied = penalty.status === 'APPLIED';
  const targetName = penalty.target === 'OVERALL' ? 'the overall score' : names.dimension(penalty.target).name;
  const targetTotal = parsed.effectiveByTarget[penalty.target];

  return (
    <div className="space-y-8">
      <PageHeader
        back={<BackLink to="/dashboard">Back to the dashboard</BackLink>}
        title={display.name}
        subtitle={display.description}
        actions={<PenaltyStateBadge status={penalty.status} />}
      />

      <Card className="space-y-5 p-6">
        <h2 className="text-base font-semibold">{applied ? 'This deduction changed the score' : 'This is a candidate, not an applied deduction'}</h2>
        <p className="text-sm text-ink-soft">
          {applied && (display.explanation ?? `${targetName} was lowered.`)}
          {!applied && <>{penaltyReason(penalty.reason)}. It did not change any score.</>}
        </p>
        <div className="space-y-3">
          <div className="grid grid-cols-[9rem_1fr_5rem] items-center gap-3 text-sm">
            <span>Requested (candidate)</span>
            <SegmentTrack label={`Requested deduction ${formatScore(penalty.candidateDeduction)}`} segments={[{ label: 'Requested', value: penalty.candidateDeduction, className: 'bg-risk-medium' }]} max={penalty.candidateDeduction > 0 ? penalty.candidateDeduction : 1} />
            <span className="text-right tabular-nums">{formatScore(penalty.candidateDeduction)}</span>
          </div>
          <div className="grid grid-cols-[9rem_1fr_5rem] items-center gap-3 text-sm">
            <span>Effective (applied)</span>
            <SegmentTrack
              label={`Effective deduction ${formatScore(penalty.effectiveDeduction)}`}
              segments={[
                { label: 'Effective', value: penalty.effectiveDeduction, className: 'bg-risk-high' },
                { label: 'Suppressed', value: penalty.suppressedAmount, className: 'bg-line' },
              ]}
              max={penalty.candidateDeduction > 0 ? penalty.candidateDeduction : 1}
            />
            <span className="text-right tabular-nums font-semibold">{formatScore(penalty.effectiveDeduction)}</span>
          </div>
          <p className="text-xs text-ink-soft">
            Suppressed amount {formatScore(penalty.suppressedAmount)}. The grey part of the second bar is what was requested but not applied.
          </p>
        </div>
      </Card>

      <Card>
        <CardHeader title="How it was decided" />
        <dl className="grid gap-x-6 gap-y-4 p-5 sm:grid-cols-2 lg:grid-cols-3">
          <div>
            <dt className="text-xs text-ink-soft">Triggering pattern</dt>
            <dd className="text-sm">
              <Link className="underline-offset-2 hover:underline" to={`/runs/${runId}/patterns/${penalty.triggerPatternId}`}>
                {triggerName.name}
              </Link>
              {trigger && <span className="ml-2 text-xs text-ink-faint">{humanizeCode(trigger.status)}</span>}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-ink-soft">Target</dt>
            <dd className="text-sm">{penalty.target === 'OVERALL' ? 'Overall score' : names.dimension(penalty.target).name}</dd>
          </div>
          <div>
            <dt className="text-xs text-ink-soft">Calculation</dt>
            <dd className="text-sm">
              {humanizeCode(penalty.strategy)}
              {penalty.scoreBase && <span className="block text-xs text-ink-faint">Based on {penalty.scoreBase}</span>}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-ink-soft">Aggregation policy</dt>
            <dd className="text-sm">{humanizeCode(penalty.stackingPolicy)}</dd>
          </div>
          <div>
            <dt className="text-xs text-ink-soft">Category and priority</dt>
            <dd className="text-sm">
              {penalty.category ? humanizeCode(penalty.category) : '\u2013'}
              {penalty.priority != null && <span className="text-xs text-ink-faint"> &middot; priority {penalty.priority}</span>}
              {penalty.exclusiveGroup && <span className="block text-xs text-ink-faint">Exclusive group {penalty.exclusiveGroup}</span>}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-ink-soft">Reason</dt>
            <dd className="text-sm">
              {penaltyReason(penalty.reason)}
              {penalty.winningPenaltyId && (
                <span className="block text-xs text-ink-faint">
                  Winning penalty:{' '}
                  {winner ? (
                    <Link className="underline" to={`/runs/${runId}/penalties/${winner.penaltyId}`}>
                      {names.penalty(winner.penaltyId, winner.code).name}
                    </Link>
                  ) : (
                    names.penalty(penalty.winningPenaltyId).name
                  )}
                </span>
              )}
            </dd>
          </div>
        </dl>
        <p className="border-t border-line px-5 py-3 text-xs text-ink-faint">The configured maximum deduction is not part of the stored calculation trace, so it is not shown here.</p>
      </Card>

      <Card>
        <CardHeader title="Aggregation result" description={`All penalties that targeted ${targetName}`} />
        <div className="overflow-x-auto p-5">
          <table className="w-full min-w-max text-sm">
            <caption className="sr-only">Penalties targeting {targetName}</caption>
            <thead>
              <tr className="border-b border-line text-left text-xs text-ink-soft">
                <th scope="col" className="py-1.5 pr-4 font-medium">Penalty</th>
                <th scope="col" className="py-1.5 pr-4 font-medium">State</th>
                <th scope="col" className="py-1.5 pr-4 font-medium">Requested</th>
                <th scope="col" className="py-1.5 pr-4 font-medium">Effective</th>
                <th scope="col" className="py-1.5 font-medium">Suppressed</th>
              </tr>
            </thead>
            <tbody>
              {peers.map((p) => (
                <tr key={`${p.penaltyId}-${p.version}`} className={`border-b border-line/60 ${p.penaltyId === penalty.penaltyId ? 'bg-accent-soft/50' : ''}`}>
                  <th scope="row" className="py-1.5 pr-4 text-left font-normal">
                    {names.penalty(p.penaltyId, p.code).name}
                  </th>
                  <td className="py-1.5 pr-4">
                    <PenaltyStateBadge status={p.status} />
                  </td>
                  <td className="py-1.5 pr-4 tabular-nums">{formatScore(p.candidateDeduction)}</td>
                  <td className="py-1.5 pr-4 tabular-nums">{formatScore(p.effectiveDeduction)}</td>
                  <td className="py-1.5 tabular-nums">{formatScore(p.suppressedAmount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {targetTotal != null && (
            <p className="mt-3 text-sm text-ink-soft">
              Effective deduction for {targetName} after aggregation: <strong className="text-ink tabular-nums">{formatScore(targetTotal)}</strong>
            </p>
          )}
        </div>
      </Card>
    </div>
  );
}
