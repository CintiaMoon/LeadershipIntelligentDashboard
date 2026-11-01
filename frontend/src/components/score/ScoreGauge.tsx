import type { RunCatalog } from '@/types/api';
import { interpretationTone } from '@/utils/labels';
import { bandSpan, formatBound, formatScore, humanizeCode, percentOf } from '@/utils/presentation';

type Band = NonNullable<RunCatalog['lisBands']>[number];

const FILL: Record<string, string> = {
  critical: 'bg-risk-critical-soft',
  high: 'bg-risk-high-soft',
  medium: 'bg-risk-medium-soft',
  ok: 'bg-ok-soft',
  neutral: 'bg-line',
  accent: 'bg-accent-soft',
  low: 'bg-risk-low-soft',
};

/** The score on its configured bands. Bands and the marker position come from the backend; this only draws them. */
export function ScoreGauge({ score, label, bands }: { score: number | null | undefined; label?: string | null; bands: Band[] }) {
  const active = bands.find((b) => b.label === label);
  const description =
    score == null
      ? 'No score'
      : `Score ${formatScore(score)}${active ? `, in the ${humanizeCode(active.label)} band (${formatBound(active.min)} to ${formatBound(active.max)})` : ''}`;
  return (
    <div>
      <div role="img" aria-label={description} className="relative">
        <div className="flex h-3 overflow-hidden rounded-full ring-1 ring-line">
          {bands.map((b) => (
            <div key={b.label} style={{ width: `${bandSpan(b.min, b.max)}%` }} className={`${FILL[interpretationTone(b.label)] ?? 'bg-line'} ${b.label === label ? 'ring-2 ring-inset ring-accent' : ''}`} />
          ))}
        </div>
        {score != null && (
          <span aria-hidden="true" className="absolute -top-1 h-5 w-1 -translate-x-1/2 rounded bg-ink" style={{ left: `${percentOf(score, 0, 100)}%` }} />
        )}
      </div>
      <ol className="mt-2 flex text-[11px] text-ink-faint" aria-hidden="true">
        {bands.map((b) => (
          <li key={b.label} style={{ width: `${bandSpan(b.min, b.max)}%` }} className={b.label === label ? 'font-semibold text-ink' : ''}>
            <span className="block truncate">{humanizeCode(b.label)}</span>
            <span className="tabular-nums">
              {formatBound(b.min)}&ndash;{formatBound(b.max)}
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}
