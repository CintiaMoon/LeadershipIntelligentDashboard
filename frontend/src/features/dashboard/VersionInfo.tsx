import type { ScoringResult } from '@/types/api';
import { formatDateTime } from '@/utils/presentation';

/** Which engine and configuration produced these numbers, so every figure can be reproduced. */
export function VersionInfo({ result }: { result: ScoringResult }) {
  return (
    <details className="rounded-lg border border-line bg-surface px-4 py-3 text-sm">
      <summary className="cursor-pointer font-medium text-ink-soft">Version information</summary>
      <dl className="mt-3 grid gap-x-6 gap-y-2 sm:grid-cols-2">
        {[
          ['Engine', result.engineVersion],
          ['Algorithm', result.algorithmVersion],
          ['Configuration', result.configurationVersion],
          ['Configuration hash', result.configurationHash],
          ['Scope', result.scope === 'TEAM' ? `Team ${result.teamId}` : 'Organization'],
          ['Scoring run', result.scoringRunId],
          ['Calculated', formatDateTime(result.calculatedAt)],
        ].map(([k, v]) => (
          <div key={k}>
            <dt className="text-xs text-ink-soft">{k}</dt>
            <dd className="break-all font-mono text-xs">{v ?? '\u2013'}</dd>
          </div>
        ))}
      </dl>
    </details>
  );
}
