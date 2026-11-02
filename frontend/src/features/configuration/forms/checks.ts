import { isRecord } from './path';

/** Advisory checks that run as the person types. The server's validation decides; these only say early what is plainly off. */

export function weightsProblem(weights: unknown, tolerance: unknown): string | undefined {
  if (!isRecord(weights)) return undefined;
  const values = Object.values(weights);
  if (values.some((v) => typeof v !== 'number')) return 'Every weight must be a number.';
  const sum = (values as number[]).reduce((a, b) => a + b, 0);
  const allowed = typeof tolerance === 'number' ? tolerance : 0.0001;
  if (values.some((v) => (v as number) < 0)) return 'A weight cannot be negative.';
  return Math.abs(sum - 1) > allowed ? `The weights add up to ${Number(sum.toFixed(6))}; they must add up to 1.` : undefined;
}

interface Band {
  min?: unknown;
  max?: unknown;
}

/** Bands are meant to cover 0 to 100 once, in order, with no gap and no overlap. */
export function bandsProblem(bands: unknown): string | undefined {
  if (!Array.isArray(bands) || bands.length === 0) return undefined;
  const list = bands as Band[];
  if (list.some((b) => typeof b.min !== 'number' || typeof b.max !== 'number')) return 'Every band needs a number for its lowest and highest score.';
  const sorted = [...list].sort((a, b) => (a.min as number) - (b.min as number));
  if (sorted.some((b) => (b.min as number) >= (b.max as number))) return 'A band must start below where it ends.';
  if (sorted[0]!.min !== 0) return 'The first band should start at 0.';
  if (sorted[sorted.length - 1]!.max !== 100) return 'The last band should end at 100.';
  for (let i = 1; i < sorted.length; i++) {
    if (sorted[i]!.min !== sorted[i - 1]!.max) return 'Bands must follow each other with no gap and no overlap.';
  }
  return undefined;
}

export const unitProblem = (v: unknown): string | undefined => (typeof v === 'number' && (v < 0 || v > 1) ? 'Use a value from 0 to 1.' : undefined);
