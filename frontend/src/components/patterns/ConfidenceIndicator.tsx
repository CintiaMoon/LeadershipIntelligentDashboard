import { CONFIDENCE_BANDS } from '@/utils/labels';
import { formatRatio, humanizeCode } from '@/utils/presentation';

const ORDER = [...CONFIDENCE_BANDS].reverse(); // INSUFFICIENT .. STRONG

/** Confidence is evidence quality, shown separately from severity (frontend-spec 6.3, 6.4). The band comes from the backend. */
export function ConfidenceIndicator({ band, value }: { band?: string | null; value?: number | null }) {
  const level = band ? ORDER.indexOf(band as (typeof ORDER)[number]) : -1;
  const label = band ? humanizeCode(band) : 'Not assessed';
  return (
    <span className="inline-flex items-center gap-2 text-xs text-ink-soft" title={value != null ? `Confidence ${formatRatio(value)}` : undefined}>
      <span aria-hidden="true" className="flex gap-0.5">
        {ORDER.map((_, i) => (
          <span key={i} className={`h-3 w-1.5 rounded-sm ${i <= level ? 'bg-accent' : 'bg-line'}`} />
        ))}
      </span>
      <span>
        Confidence: <span className="font-medium text-ink">{label}</span>
        {value != null && <span className="text-ink-faint"> ({formatRatio(value)})</span>}
      </span>
    </span>
  );
}
