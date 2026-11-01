import { SeriesChart, type SeriesRow } from '@/components/charts/SeriesChart';
import { chartColors } from '@/components/charts/palette';
import { Card, CardHeader } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/StateViews';
import type { Trends } from '@/types/api';
import { DIMENSIONS, SEVERITIES, comparabilityReason } from '@/utils/labels';
import { formatDateTime, formatScore, humanizeCode } from '@/utils/presentation';
import type { Names } from './display';

const whole = (v: number | null) => (v === null ? '\u2013' : String(v));

/** Historical views. Every figure is a stored value of an earlier run; runs with different configurations are flagged. */
export function TrendsSection({ trends, names }: { trends: Trends; names: Names }) {
  const points = trends.points ?? [];
  if (points.length < 2) {
    return (
      <Card>
        <CardHeader title="Historical trends" />
        <div className="p-5">
          <EmptyState title="Trends appear after two assessments" description="Once this organization has been assessed again, its evolution is shown here." />
        </div>
      </Card>
    );
  }

  // x labels must be unique, so a repeated timestamp gets its position appended
  const labels = points.map((p, i) => {
    const base = formatDateTime(p.run?.calculatedAt);
    return points.some((q, j) => j !== i && formatDateTime(q.run?.calculatedAt) === base) ? `${base} #${i + 1}` : base;
  });
  const changes = points.flatMap((p, i) => (p.run?.previousScoringRunId && p.run.comparable === false ? [{ index: i, reasons: p.run.notComparableReasons ?? [] }] : []));

  const scoreRows: SeriesRow[] = points.map((p, i) => ({ label: labels[i] as string, score: p.run?.leadershipIntelligenceScore ?? null }));
  const dimensionRows: SeriesRow[] = points.map((p, i) => ({
    label: labels[i] as string,
    ...Object.fromEntries(DIMENSIONS.map((d) => [d, p.dimensions?.find((x) => x.dimension === d)?.adjustedScore ?? null])),
  }));
  const riskRows: SeriesRow[] = points.map((p, i) => ({ label: labels[i] as string, ...Object.fromEntries(SEVERITIES.map((s) => [s, p.patternsBySeverity?.[s] ?? 0])) }));
  const penaltyRows: SeriesRow[] = points.map((p, i) => ({
    label: labels[i] as string,
    effective: p.penalties?.effectiveDeduction ?? null,
    suppressed: p.penalties?.suppressedAmount ?? null,
  }));

  const occurrences = new Map<string, number>();
  for (const p of points) for (const hit of p.patterns ?? []) occurrences.set(hit.patternId ?? '', (occurrences.get(hit.patternId ?? '') ?? 0) + 1);
  const frequency = [...occurrences.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));

  const first = points[0]?.run?.leadershipIntelligenceScore;
  const last = points[points.length - 1]?.run?.leadershipIntelligenceScore;

  return (
    <section aria-labelledby="trends-heading">
      <Card>
        <CardHeader title="Historical trends" description={`${points.length} assessments, oldest to newest`} />
        <span id="trends-heading" className="sr-only">
          Historical trends
        </span>
        <div className="space-y-8 p-5">
          {changes.length > 0 && (
            <div role="note" className="rounded-lg border border-risk-medium/40 bg-risk-medium-soft px-4 py-3 text-sm">
              <p className="font-semibold">These assessments were not scored the same way</p>
              <ul className="mt-1 list-disc pl-5 text-ink-soft">
                {changes.map((c) => (
                  <li key={c.index}>
                    Before {labels[c.index]}: {c.reasons.map(comparabilityReason).join('; ') || 'the scoring basis changed'}. Differences across this point are not a like-for-like change.
                  </li>
                ))}
              </ul>
            </div>
          )}
          <SeriesChart
            title="Leadership Intelligence Score"
            summary={`From ${formatScore(first)} to ${formatScore(last)} across ${points.length} assessments.`}
            rows={scoreRows}
            series={[{ key: 'score', name: 'LIS', color: chartColors.score }]}
            kind="line"
            domain={[0, 100]}
            markers={changes.map((c) => labels[c.index] as string)}
            format={(v) => formatScore(v)}
          />
          <SeriesChart
            title="Dimensions (adjusted scores)"
            summary="Each line is one dimension's adjusted score at every assessment."
            rows={dimensionRows}
            series={DIMENSIONS.map((d, i) => ({ key: d, name: names.dimension(d).name, color: chartColors.dimensions[d] ?? chartColors.score, dash: i % 2 ? '5 3' : undefined }))}
            kind="line"
            domain={[0, 100]}
            markers={changes.map((c) => labels[c.index] as string)}
            format={(v) => formatScore(v)}
          />
          <SeriesChart
            title="Risk evolution (patterns detected by severity)"
            summary="Number of detected patterns at each severity, per assessment."
            rows={riskRows}
            series={SEVERITIES.map((s) => ({ key: s, name: humanizeCode(s), color: chartColors.severity[s] ?? chartColors.axis }))}
            kind="stacked-bar"
            format={whole}
          />
          <SeriesChart
            title="Penalty evolution"
            summary="Effective deductions applied, and amounts suppressed, per assessment."
            rows={penaltyRows}
            series={[
              { key: 'effective', name: 'Effective deduction', color: chartColors.deduction },
              { key: 'suppressed', name: 'Suppressed amount', color: chartColors.suppressed },
            ]}
            kind="bar"
            format={(v) => formatScore(v)}
          />
          <div>
            <h3 className="text-sm font-medium">Pattern frequency</h3>
            {frequency.length === 0 ? (
              <p className="mt-1 text-sm text-ink-soft">No patterns were detected in these assessments.</p>
            ) : (
              <ul className="mt-2 divide-y divide-line rounded-lg border border-line text-sm">
                {frequency.map(([id, count]) => (
                  <li key={id} className="flex justify-between gap-3 px-3 py-2">
                    <span>{names.pattern(id).name}</span>
                    <span className="tabular-nums text-ink-soft">
                      detected in {count} of {points.length} assessments
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </Card>
    </section>
  );
}
