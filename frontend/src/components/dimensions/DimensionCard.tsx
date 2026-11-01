import { Link } from 'react-router-dom';
import { Sparkline } from '@/components/charts/Sparkline';
import { SeverityBadge } from '@/components/patterns/SeverityBadge';
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import type { Display } from '@/features/dashboard/display';
import type { DimensionRisk } from '@/features/dashboard/selectors';
import type { DimensionResult } from '@/types/api';
import type { DimensionDetail } from '@/types/views';
import { interpretationTone } from '@/utils/labels';
import { byDescending, formatChange, formatRatio, formatScore, humanizeCode } from '@/utils/presentation';
import type { Names } from '@/features/dashboard/display';

interface Props {
  dimension: Display;
  result?: DimensionResult;
  detail?: DimensionDetail;
  names: Names;
  history: (number | null | undefined)[];
  /** Change since the previous comparable assessment, from the backend comparison; absent when not comparable. */
  change?: number | null;
  risk: DimensionRisk;
  redacted: boolean;
  /** where the detailed analysis of this dimension lives */
  detailHref?: string;
}

export function DimensionCard({ dimension, result, detail, names, history, change, risk, redacted, detailHref }: Props) {
  const insufficient = !result || result.status !== 'VALID';
  const contributions = [...(detail?.score.contributions ?? [])]
    .filter((c) => c.value != null)
    .sort(byDescending((c) => c.contribution))
    .slice(0, 3);
  const shown = history.filter((v): v is number => typeof v === 'number');

  return (
    <Card className="flex h-full flex-col p-5">
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-base font-semibold">{dimension.name}</h3>
        {!insufficient && result?.label && <Badge tone={interpretationTone(result.label)}>{humanizeCode(result.label)}</Badge>}
      </div>
      {dimension.description && <p className="mt-1 text-xs text-ink-soft">{dimension.description}</p>}

      {insufficient ? (
        <div className="mt-4">
          <p className="text-lg font-semibold">Not enough data</p>
          <p className="text-xs text-ink-soft">Coverage {formatRatio(result?.coverage)}. No score is shown rather than an estimate.</p>
        </div>
      ) : (
        <div className="mt-4">
          <p className="text-4xl font-semibold tabular-nums">{formatScore(result?.adjustedScore)}</p>
          <p className="mt-1 text-xs text-ink-soft tabular-nums">
            Raw {formatScore(result?.rawScore)}
            {result?.effectivePenalty ? <> &middot; penalty {formatScore(result.effectivePenalty)}</> : null}
          </p>
          {change != null && (
            <p className="mt-1 text-xs text-ink-soft">
              Since the previous assessment: <strong className="text-ink tabular-nums">{formatChange(change)}</strong>
            </p>
          )}
        </div>
      )}

      <div className="mt-3">
        <Sparkline values={history} summary={shown.length >= 2 ? `${dimension.name}, oldest to newest: ${shown.map(formatScore).join(', ')}.` : `${dimension.name} trend not available yet.`} />
      </div>

      <div className="mt-4 space-y-1.5 border-t border-line pt-3">
        <p className="text-xs font-medium text-ink-soft">Risk</p>
        {risk.highestSeverity ? (
          <p className="flex flex-wrap items-center gap-2 text-sm">
            <SeverityBadge severity={risk.highestSeverity} />
            <span className="text-ink-soft">
              {risk.detected} {risk.detected === 1 ? 'pattern' : 'patterns'} detected
            </span>
          </p>
        ) : (
          <p className="text-sm text-ink-soft">No risk patterns detected</p>
        )}
        {(risk.appliedPenalties > 0 || risk.suppressedPenalties > 0) && (
          <p className="text-xs text-ink-soft">
            Penalties: {risk.appliedPenalties} applied, {risk.suppressedPenalties} suppressed
          </p>
        )}
      </div>

      <div className="mt-3 border-t border-line pt-3">
        <p className="text-xs font-medium text-ink-soft">Key contributing signals</p>
        {redacted ? (
          <p className="mt-1 text-xs text-ink-faint">Question-level detail is hidden for your role.</p>
        ) : contributions.length === 0 ? (
          <p className="mt-1 text-xs text-ink-faint">No contributions recorded.</p>
        ) : (
          <ul className="mt-1 space-y-1 text-sm">
            {contributions.map((c) => (
              <li key={c.questionId} className="flex justify-between gap-3">
                <span className="truncate">{names.question(c.questionId).name}</span>
                <span className="tabular-nums text-ink-soft">{formatScore(c.value)}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
      {detailHref && (
        <Link className="mt-auto pt-4 text-sm font-medium text-accent underline-offset-2 hover:underline" to={detailHref} aria-label={`Detailed analysis: ${dimension.name}`}>
          Detailed analysis &rarr;
        </Link>
      )}
    </Card>
  );
}
