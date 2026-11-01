import { percentOf } from '@/utils/presentation';

export interface Segment {
  label: string;
  /** a value exactly as the backend returned it */
  value: number | null | undefined;
  className: string;
  /** drawn as empty space: used to line a segment up after earlier ones without adding any numbers */
  invisible?: boolean;
}

/**
 * A 0..100 track whose segments are laid side by side by the browser, each as wide as its own backend value. No sum is
 * ever calculated here: placement after earlier segments is done by layout, not arithmetic.
 */
export function SegmentTrack({ segments, max = 100, label }: { segments: Segment[]; max?: number; label: string }) {
  return (
    <div role="img" aria-label={label} className="flex h-5 w-full overflow-hidden rounded bg-canvas ring-1 ring-line">
      {segments.map((s, i) =>
        s.value == null ? null : (
          <div
            key={`${s.label}-${i}`}
            title={s.invisible ? undefined : s.label}
            style={{ width: `${percentOf(s.value, 0, max)}%` }}
            className={s.invisible ? 'bg-transparent' : s.className}
          />
        ),
      )}
    </div>
  );
}
