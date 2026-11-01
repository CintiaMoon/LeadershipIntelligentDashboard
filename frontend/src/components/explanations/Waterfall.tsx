import type { AdjustedDimension } from '@/types/trace';
import { formatScore } from '@/utils/presentation';
import { SegmentTrack, type Segment } from './StackedBar';

const COLORS = ['bg-accent', 'bg-ok', 'bg-risk-high', 'bg-risk-low'];

interface Props {
  dimensions: AdjustedDimension[];
  names: (dimension: string) => string;
  weightedAdjustedScore?: number | null;
  overallPenalty?: number | null;
  finalScore?: number | null;
}

/**
 * How the final score is built, as a waterfall: each dimension adds its weighted contribution, the overall penalty
 * takes some away, and the result is the LIS. Bars are positioned by laying earlier (invisible) values in front, so the
 * figure uses only numbers the backend stored; the table beside it lists the same values.
 */
export function Waterfall({ dimensions, names, weightedAdjustedScore, overallPenalty, finalScore }: Props) {
  const rows = dimensions.filter((d) => d.weightedContribution != null);
  const summary = `Waterfall of the final score: ${rows.map((d) => `${names(d.dimension)} adds ${formatScore(d.weightedContribution)}`).join(', ')}; weighted adjusted score ${formatScore(weightedAdjustedScore)}; overall penalty ${formatScore(overallPenalty)}; final score ${formatScore(finalScore)}.`;

  const steps: { label: string; value: number | null | undefined; sign: string; before: Segment[]; className: string }[] = rows.map((d, i) => ({
    label: names(d.dimension),
    value: d.weightedContribution,
    sign: '+',
    before: rows.slice(0, i).map((p) => ({ label: names(p.dimension), value: p.weightedContribution, className: '', invisible: true })),
    className: COLORS[i % COLORS.length] ?? 'bg-accent',
  }));

  return (
    <figure>
      <figcaption className="sr-only">{summary}</figcaption>
      <ol className="space-y-2">
        {steps.map((s) => (
          <li key={s.label} className="grid grid-cols-[9rem_1fr_5rem] items-center gap-3 text-sm">
            <span className="truncate">{s.label}</span>
            <SegmentTrack label={`${s.label} adds ${formatScore(s.value)}`} segments={[...s.before, { label: s.label, value: s.value, className: s.className }]} />
            <span className="text-right tabular-nums">
              {s.sign}
              {formatScore(s.value)}
            </span>
          </li>
        ))}
        <li className="grid grid-cols-[9rem_1fr_5rem] items-center gap-3 border-t border-line pt-2 text-sm font-medium">
          <span>Weighted adjusted score</span>
          <SegmentTrack label={`Weighted adjusted score ${formatScore(weightedAdjustedScore)}`} segments={[{ label: 'Weighted adjusted score', value: weightedAdjustedScore, className: 'bg-ink-soft' }]} />
          <span className="text-right tabular-nums">{formatScore(weightedAdjustedScore)}</span>
        </li>
        <li className="grid grid-cols-[9rem_1fr_5rem] items-center gap-3 text-sm">
          <span>Overall penalty</span>
          <SegmentTrack
            label={`Overall penalty ${formatScore(overallPenalty)}`}
            segments={[
              { label: 'Final score', value: finalScore, className: '', invisible: true },
              { label: 'Overall penalty', value: overallPenalty, className: 'bg-risk-critical' },
            ]}
          />
          <span className="text-right tabular-nums">&minus;{formatScore(overallPenalty)}</span>
        </li>
        <li className="grid grid-cols-[9rem_1fr_5rem] items-center gap-3 border-t border-line pt-2 text-sm font-semibold">
          <span>Final LIS</span>
          <SegmentTrack label={`Final score ${formatScore(finalScore)}`} segments={[{ label: 'Final LIS', value: finalScore, className: 'bg-accent-ink' }]} />
          <span className="text-right tabular-nums">{formatScore(finalScore)}</span>
        </li>
      </ol>
    </figure>
  );
}
