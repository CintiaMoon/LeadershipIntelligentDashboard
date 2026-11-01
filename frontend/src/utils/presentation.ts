// Presentation helpers only. Canonical scoring values are never calculated in the frontend: these functions format
// what the backend returned. This file is the only place where arithmetic on score-like values may appear.

type Numeric = number | string | null | undefined;

const EMPTY = '\u2013';

function toNumber(value: Numeric): number | null {
  if (value === null || value === undefined || value === '') return null;
  const n = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}

/** A score with the backend's two-decimal scale, e.g. 67 -> "67.00". Missing values are shown as a dash, never as 0. */
export function formatScore(value: Numeric): string {
  const n = toNumber(value);
  return n === null ? EMPTY : n.toFixed(2);
}

/** A shorter form for dense charts and tiles, e.g. 67.25 -> "67.3". Purely cosmetic. */
export function formatScoreCompact(value: Numeric): string {
  const n = toNumber(value);
  return n === null ? EMPTY : n.toFixed(1);
}

/** A signed change as supplied by the backend, e.g. 4.5 -> "+4.50", -2 -> "-2.00". Zero is shown without a sign. */
export function formatChange(value: Numeric): string {
  const n = toNumber(value);
  if (n === null) return EMPTY;
  const text = Math.abs(n).toFixed(2);
  if (n > 0) return `+${text}`;
  if (n < 0) return `-${text}`;
  return text;
}

/** Share of a bar or gauge that a value covers within a band: chart positioning only. */
export function percentOf(value: Numeric, min: number, max: number): number {
  const n = toNumber(value);
  if (n === null || max <= min) return 0;
  return Math.min(100, Math.max(0, ((n - min) / (max - min)) * 100));
}

const dateFormat = new Intl.DateTimeFormat('en', { year: 'numeric', month: 'short', day: 'numeric' });
const dateTimeFormat = new Intl.DateTimeFormat('en', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return EMPTY;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? EMPTY : dateFormat.format(d);
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return EMPTY;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? EMPTY : dateTimeFormat.format(d);
}

/** BURNOUT_RISK -> "Burnout risk": only for codes the backend catalog has no display name for. */
export function humanizeCode(code: string | null | undefined): string {
  if (!code) return EMPTY;
  const text = code.replace(/[_-]+/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** A 0..1 ratio from the backend (confidence, coverage) as a percentage, e.g. 0.75 -> "75%". */
export function formatRatio(value: Numeric): string {
  const n = toNumber(value);
  return n === null ? EMPTY : `${Math.round(n * 100)}%`;
}

/** Width of a score band on a 0..100 scale, for drawing a gauge: chart positioning only. */
export function bandSpan(min: Numeric, max: Numeric): number {
  return percentOf(max, 0, 100) - percentOf(min, 0, 100);
}

/** A range bound as the configuration wrote it: 60 -> "60", 62.5 -> "62.5". */
export function formatBound(value: Numeric): string {
  const n = toNumber(value);
  return n === null ? EMPTY : String(n);
}

/** Sort comparator for "largest first" by a backend-supplied number; missing values go last. Ordering only, no arithmetic on results. */
export function byDescending<T>(pick: (item: T) => number | null | undefined): (a: T, b: T) => number {
  return (a, b) => {
    const x = pick(a);
    const y = pick(b);
    if (x == null && y == null) return 0;
    if (x == null) return 1;
    if (y == null) return -1;
    return x === y ? 0 : x > y ? -1 : 1;
  };
}
