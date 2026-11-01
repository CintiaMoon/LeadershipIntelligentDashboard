import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '@/app/providers/auth-context';
import { SeriesChart } from '@/components/charts/SeriesChart';
import { chartColors } from '@/components/charts/palette';
import { SeverityBadge } from '@/components/patterns/SeverityBadge';
import { PenaltyStateBadge } from '@/components/penalties/PenaltyStateBadge';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { BackLink, PageHeader } from '@/components/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateViews';
import { useRunDetails } from '@/features/details/useRunDetails';
import { useAnswers, useComparison, useTrends } from '@/services/queries/hooks';
import { canSeeEvidence } from '@/services/auth/principal';
import { DIMENSIONS, interpretationTone, penaltyReason } from '@/utils/labels';
import { formatChange, formatDateTime, formatRatio, formatScore, humanizeCode } from '@/utils/presentation';
import { detectedPatterns } from '@/features/dashboard/selectors';
import { SourceAnswers } from './SourceAnswers';

export function DimensionDetailPage() {
  const { runId, dimension = '' } = useParams();
  const { principal } = useAuth();
  const details = useRunDetails(runId);
  const evidenceAllowed = canSeeEvidence(principal);
  const answers = useAnswers(runId, evidenceAllowed);
  const [open, setOpen] = useState<string | null>(null);

  const run = details.run.data;
  const trends = useTrends(run?.organizationId ?? null, { limit: 12 });
  const points = trends.data?.points ?? [];
  const here = points.find((p) => p.run?.scoringRunId === runId);
  const comparison = useComparison(here?.run?.comparable ? here.run.previousScoringRunId : undefined, runId);

  if (!(DIMENSIONS as readonly string[]).includes(dimension)) {
    return <EmptyState title="Unknown dimension" description="This dimension does not exist." action={<BackLink to="/dashboard">Back to the dashboard</BackLink>} />;
  }
  if (details.run.isPending || details.dimensions.isPending) return <LoadingState label="Loading the dimension" />;
  if (details.run.isError) return <ErrorState error={details.run.error} onRetry={() => void details.run.refetch()} />;
  if (details.dimensions.isError) return <ErrorState error={details.dimensions.error} onRetry={() => void details.dimensions.refetch()} />;
  if (details.parsed.error) return <ErrorState error={details.parsed.error} />;

  const { names, parsed, catalog } = details;
  const info = names.dimension(dimension);
  const result = run?.dimensions?.[dimension];
  const detail = parsed.details.find((d) => d.score.dimension === dimension);
  const contributions = detail?.score.contributions ?? [];
  const band = catalog.data?.dimensionBands?.find((b) => b.label === result?.label);
  const own = detectedPatterns(parsed.patterns).filter((p) => p.category === dimension);
  const targeted = parsed.penalties.filter((p) => p.target === dimension && p.status !== 'NOT_TRIGGERED');
  const change = comparison.data?.comparable ? comparison.data.dimensions?.find((d) => d.dimension === dimension)?.change : undefined;
  const recs = (details.recommendations.data?.recommendations ?? []).filter((r) => r.sources?.some((s) => own.some((p) => p.patternId === s.id)));
  const insufficient = !result || result.status !== 'VALID';

  const labels = points.map((p, i) => {
    const base = formatDateTime(p.run?.calculatedAt);
    return points.some((q, j) => j !== i && formatDateTime(q.run?.calculatedAt) === base) ? `${base} #${i + 1}` : base;
  });
  const rows = points.map((p, i) => {
    const d = p.dimensions?.find((x) => x.dimension === dimension);
    return { label: labels[i] as string, raw: d?.rawScore ?? null, adjusted: d?.adjustedScore ?? null };
  });
  const marks = points.flatMap((p, i) => (p.run?.previousScoringRunId && p.run.comparable === false ? [labels[i] as string] : []));
  const answerByQuestion = new Map<string, NonNullable<typeof answers.data>['answers']>();
  for (const a of answers.data?.answers ?? []) {
    answerByQuestion.set(a.questionId ?? '', [...(answerByQuestion.get(a.questionId ?? '') ?? []), a]);
  }

  return (
    <div className="space-y-8">
      <PageHeader back={<BackLink to="/dashboard">Back to the dashboard</BackLink>} title={info.name} subtitle={info.description} />

      <Card className="p-6">
        {insufficient ? (
          <div>
            <p className="text-2xl font-semibold">Not enough data</p>
            <p className="mt-1 text-sm text-ink-soft">Coverage {formatRatio(result?.coverage)}. No score is shown rather than an estimate.</p>
          </div>
        ) : (
          <div className="grid gap-6 sm:grid-cols-4">
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-ink-soft">Adjusted score</p>
              <p className="mt-1 text-5xl font-semibold tabular-nums" data-testid="adjusted-score">
                {formatScore(result?.adjustedScore)}
              </p>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                {result?.label && <Badge tone={interpretationTone(result.label)}>{humanizeCode(result.label)}</Badge>}
                {band && (
                  <span className="text-xs text-ink-soft">
                    Band {band.min}&ndash;{band.max}
                  </span>
                )}
              </div>
            </div>
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-ink-soft">Raw score</p>
              <p className="mt-1 text-3xl font-semibold tabular-nums">{formatScore(result?.rawScore)}</p>
              <p className="text-xs text-ink-soft">before penalties</p>
            </div>
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-ink-soft">Penalty applied</p>
              <p className="mt-1 text-3xl font-semibold tabular-nums">{formatScore(result?.effectivePenalty)}</p>
              <p className="text-xs text-ink-soft">effective deduction</p>
            </div>
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-ink-soft">Since the previous assessment</p>
              <p className="mt-1 text-3xl font-semibold tabular-nums">{change != null ? formatChange(change) : '\u2013'}</p>
              <p className="text-xs text-ink-soft">{change != null ? 'comparable assessments' : 'shown only for comparable assessments'}</p>
            </div>
          </div>
        )}
      </Card>

      {rows.length >= 2 && (
        <Card className="p-5">
          <SeriesChart
            title={`${info.name} over time`}
            summary="Raw and adjusted score at each assessment."
            rows={rows}
            series={[
              { key: 'adjusted', name: 'Adjusted', color: chartColors.dimensions[dimension] ?? chartColors.score },
              { key: 'raw', name: 'Raw', color: chartColors.axis, dash: '5 3' },
            ]}
            kind="line"
            domain={[0, 100]}
            markers={marks}
            format={(v) => formatScore(v)}
          />
        </Card>
      )}

      <Card>
        <CardHeader title="Question contributions" description="Each question mapped to this dimension, with its value, weight and contribution" />
        <div className="overflow-x-auto p-5">
          {contributions.length === 0 ? (
            <EmptyState title="Question detail is not available" description={details.dimensionsRedacted ? 'It is hidden for your role.' : 'No contributions were recorded.'} />
          ) : (
            <table className="w-full min-w-max text-sm">
              <caption className="sr-only">Contributions to {info.name}</caption>
              <thead>
                <tr className="border-b border-line text-left text-xs text-ink-soft">
                  <th scope="col" className="py-1.5 pr-4 font-medium">Question</th>
                  <th scope="col" className="py-1.5 pr-4 font-medium">Normalized value</th>
                  <th scope="col" className="py-1.5 pr-4 font-medium">Weight</th>
                  <th scope="col" className="py-1.5 pr-4 font-medium">Share of weight</th>
                  <th scope="col" className="py-1.5 pr-4 font-medium">Contribution</th>
                  <th scope="col" className="py-1.5 font-medium">
                    <span className="sr-only">Source answer</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {contributions.map((c) => {
                  const expanded = open === c.questionId;
                  const positive = detail?.positiveDrivers.includes(c.questionId);
                  const negative = detail?.negativeDrivers.includes(c.questionId);
                  return (
                    <tr key={c.questionId} className="border-b border-line/60 align-top">
                      <th scope="row" className="py-2 pr-4 text-left font-normal">
                        {names.question(c.questionId).name}
                        {positive && <Badge tone="ok">Positive signal</Badge>}
                        {negative && <Badge tone="high">Negative signal</Badge>}
                        {expanded && (
                          <div className="mt-2 rounded-lg bg-canvas px-3 py-2 text-sm">
                            {answers.isPending ? (
                              <span>Loading the answer&hellip;</span>
                            ) : answers.isError ? (
                              <span>The answer could not be loaded.</span>
                            ) : (
                              <SourceAnswers views={answerByQuestion.get(c.questionId) ?? []} />
                            )}
                          </div>
                        )}
                      </th>
                      <td className="py-2 pr-4 tabular-nums">{formatScore(c.value)}</td>
                      <td className="py-2 pr-4 tabular-nums">{c.weight ?? '\u2013'}</td>
                      <td className="py-2 pr-4 tabular-nums">{formatRatio(c.normalizedWeight)}</td>
                      <td className="py-2 pr-4 tabular-nums font-medium">{formatScore(c.contribution)}</td>
                      <td className="py-2">
                        {evidenceAllowed && (
                          <Button variant="ghost" aria-expanded={expanded} onClick={() => setOpen(expanded ? null : c.questionId)}>
                            {expanded ? 'Hide answer' : 'Show answer'}
                          </Button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
          {!evidenceAllowed && contributions.length > 0 && <p className="mt-3 text-xs text-ink-faint">Source answers are available to owners, administrators and analysts.</p>}
        </div>
      </Card>

      <div className="grid gap-8 lg:grid-cols-2">
        <Card>
          <CardHeader title="Detected patterns" />
          <div className="space-y-3 p-5">
            {own.length === 0 ? (
              <p className="text-sm text-ink-soft">No patterns were detected for this dimension.</p>
            ) : (
              own.map((p) => (
                <div key={p.patternId} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                  <Link className="font-medium underline-offset-2 hover:underline" to={`/runs/${runId}/patterns/${p.patternId}`}>
                    {names.pattern(p.patternId, p.code).name}
                  </Link>
                  <SeverityBadge severity={p.severity} />
                </div>
              ))
            )}
          </div>
        </Card>
        <Card>
          <CardHeader title="Penalties on this dimension" description="Applied and suppressed" />
          <div className="space-y-3 p-5">
            {targeted.length === 0 ? (
              <p className="text-sm text-ink-soft">No penalty was requested for this dimension.</p>
            ) : (
              targeted.map((p) => (
                <div key={`${p.penaltyId}-${p.version}`} className="text-sm">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <Link className="font-medium underline-offset-2 hover:underline" to={`/runs/${runId}/penalties/${p.penaltyId}`}>
                      {names.penalty(p.penaltyId, p.code).name}
                    </Link>
                    <PenaltyStateBadge status={p.status} />
                  </div>
                  <p className="text-xs text-ink-soft">
                    Requested {formatScore(p.candidateDeduction)}, effective {formatScore(p.effectiveDeduction)}, suppressed {formatScore(p.suppressedAmount)}. {penaltyReason(p.reason)}.
                  </p>
                </div>
              ))
            )}
          </div>
        </Card>
      </div>

      <Card>
        <CardHeader title="Recommended actions" description="From the patterns detected in this dimension" />
        <div className="space-y-3 p-5">
          {recs.length === 0 ? (
            <p className="text-sm text-ink-soft">No recommended actions for this dimension.</p>
          ) : (
            recs.map((r) => (
              <div key={r.key}>
                <p className="text-sm font-semibold">{r.title}</p>
                <p className="text-sm text-ink-soft">{r.description}</p>
              </div>
            ))
          )}
        </div>
      </Card>
    </div>
  );
}
