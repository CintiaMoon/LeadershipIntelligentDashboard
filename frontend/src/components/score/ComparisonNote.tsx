import type { RunSummary } from '@/types/api';
import { comparabilityReason } from '@/utils/labels';
import { formatChange, formatScore } from '@/utils/presentation';

/**
 * Comparison with the previous assessment. A change is shown only when the backend says the two runs are comparable
 * (decision D27); otherwise the reasons are named instead of showing a misleading delta.
 */
export function ComparisonNote({ summary }: { summary?: RunSummary }) {
  if (!summary || !summary.previousScoringRunId) {
    return <p className="text-sm text-ink-soft">First assessment in this series, so there is nothing to compare with yet.</p>;
  }
  if (summary.comparable) {
    return (
      <p className="text-sm text-ink-soft">
        Compared with the previous assessment ({formatScore(summary.previousScore)}):{' '}
        <strong className="font-semibold text-ink tabular-nums">{formatChange(summary.change)}</strong>
      </p>
    );
  }
  const reasons = (summary.notComparableReasons ?? []).map(comparabilityReason);
  return (
    <p className="text-sm text-ink-soft" role="note">
      <strong className="font-semibold text-ink">Not comparable with the previous assessment ({formatScore(summary.previousScore)})</strong>
      {reasons.length > 0 && <>: {reasons.join('; ')}</>}. No change is shown.
    </p>
  );
}
