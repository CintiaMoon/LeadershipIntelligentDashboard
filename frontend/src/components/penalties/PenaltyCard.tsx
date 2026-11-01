import { Link } from 'react-router-dom';
import { PenaltyStateBadge } from '@/components/penalties/PenaltyStateBadge';
import type { Names } from '@/features/dashboard/display';
import type { Penalty } from '@/types/views';
import { penaltyReason } from '@/utils/labels';
import { formatScore, humanizeCode } from '@/utils/presentation';

/**
 * One penalty with its requested (candidate) and effective deduction side by side, so a candidate is never mistaken
 * for an applied deduction, and a suppressed amount is always explained.
 */
export function PenaltyCard({ penalty, names, detailHref }: { penalty: Penalty; names: Names; detailHref?: string }) {
  const display = names.penalty(penalty.penaltyId, penalty.code);
  const trigger = names.pattern(penalty.triggerPatternId);
  const applied = penalty.status === 'APPLIED';
  const suppressed = penalty.status === 'SUPPRESSED';

  return (
    <article className={`rounded-lg border p-4 ${suppressed ? 'border-risk-medium/40 bg-risk-medium-soft/40' : 'border-line bg-surface'}`} aria-label={display.name}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h4 className="text-sm font-semibold">{display.name}</h4>
        <PenaltyStateBadge status={penalty.status} />
      </div>
      {display.description && <p className="mt-1 text-sm text-ink-soft">{display.description}</p>}

      <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm sm:grid-cols-4">
        <div>
          <dt className="text-xs text-ink-soft">Target</dt>
          <dd>{penalty.target === 'OVERALL' ? 'Overall score' : names.dimension(penalty.target).name}</dd>
        </div>
        <div>
          <dt className="text-xs text-ink-soft">Requested deduction</dt>
          <dd className="tabular-nums">{formatScore(penalty.candidateDeduction)}</dd>
        </div>
        <div>
          <dt className="text-xs text-ink-soft">Effective deduction</dt>
          <dd className={`tabular-nums ${applied ? 'font-semibold' : ''}`}>{formatScore(penalty.effectiveDeduction)}</dd>
        </div>
        <div>
          <dt className="text-xs text-ink-soft">Suppressed amount</dt>
          <dd className="tabular-nums">{formatScore(penalty.suppressedAmount)}</dd>
        </div>
      </dl>

      <p className="mt-3 text-sm">
        <span className="text-ink-soft">Why: </span>
        {penaltyReason(penalty.reason)}
        {suppressed && penalty.winningPenaltyId && <span className="text-ink-soft"> (winning penalty: {names.penalty(penalty.winningPenaltyId).name})</span>}.
      </p>
      {display.explanation && applied && <p className="mt-1 text-sm text-ink-soft">{display.explanation}</p>}
      <p className="mt-2 text-xs text-ink-faint">
        Triggered by <a className="underline hover:text-ink" href={`#pattern-${penalty.triggerPatternId}`}>{trigger.name}</a> &middot; aggregation policy {humanizeCode(penalty.stackingPolicy)}
      </p>
      {detailHref && (
        <Link className="mt-2 inline-block text-sm font-medium text-accent underline-offset-2 hover:underline" to={detailHref} aria-label={`Penalty details: ${display.name}`}>
          Penalty details
        </Link>
      )}
    </article>
  );
}
