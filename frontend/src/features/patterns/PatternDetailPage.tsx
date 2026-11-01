import { Link, useParams } from 'react-router-dom';
import { ConditionTree } from '@/components/patterns/ConditionTree';
import { ConfidenceIndicator } from '@/components/patterns/ConfidenceIndicator';
import { SeverityBadge } from '@/components/patterns/SeverityBadge';
import { EvidenceList } from '@/components/evidence/EvidenceList';
import { PenaltyStateBadge } from '@/components/penalties/PenaltyStateBadge';
import { Badge } from '@/components/ui/Badge';
import { Card, CardHeader } from '@/components/ui/Card';
import { BackLink, PageHeader } from '@/components/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateViews';
import { useRunDetails } from '@/features/details/useRunDetails';
import { affectedDimensions } from '@/features/dashboard/selectors';
import { patternReason, penaltyReason } from '@/utils/labels';
import { formatRatio, formatScore, humanizeCode } from '@/utils/presentation';

/** Why a pattern was (or was not) detected, in terms an engineering leader can act on. */
const COUNTS = new Set(['independentGroups', 'supportingSignals', 'respondents']);

/** Ratios are shown as percentages, counts as numbers, and an unknown respondent count as unknown (never as zero or null). */
function breakdownValue(key: string, value: unknown): string {
  if (value === null || value === undefined) return key === 'respondents' ? 'Not known' : '–';
  if (typeof value === 'number' && value <= 1 && !COUNTS.has(key)) return formatRatio(value);
  return String(value);
}

export function PatternDetailPage() {
  const { runId, patternId } = useParams();
  const d = useRunDetails(runId);

  if (d.patterns.isPending || d.penalties.isPending) return <LoadingState label="Loading the pattern" />;
  if (d.patterns.isError) return <ErrorState error={d.patterns.error} onRetry={() => void d.patterns.refetch()} />;
  if (d.penalties.isError) return <ErrorState error={d.penalties.error} onRetry={() => void d.penalties.refetch()} />;
  if (d.parsed.error) return <ErrorState error={d.parsed.error} />;

  const { names, parsed } = d;
  const pattern = parsed.patterns.find((p) => p.patternId === patternId);
  if (!pattern) {
    return <EmptyState title="Pattern not found" description="This pattern is not part of the selected run." action={<BackLink to="/dashboard">Back to the dashboard</BackLink>} />;
  }

  const display = names.pattern(pattern.patternId, pattern.code);
  const detected = pattern.status === 'MATCHED';
  const keys = new Set(pattern.evidence.map((e) => e.operandKey));
  const related = parsed.patterns.filter(
    (p) => p.patternId !== pattern.patternId && p.status === 'MATCHED' && (p.category === pattern.category || p.evidence.some((e) => keys.has(e.operandKey))),
  );
  const penalties = parsed.penalties.filter((p) => p.triggerPatternId === pattern.patternId);
  const actions = (d.recommendations.data?.recommendations ?? []).filter((r) => r.sources?.some((s) => s.id === pattern.patternId));
  const affected = affectedDimensions(pattern, parsed.penalties);

  return (
    <div className="space-y-8">
      <PageHeader
        back={<BackLink to="/dashboard">Back to the dashboard</BackLink>}
        title={display.name}
        subtitle={display.description}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <SeverityBadge severity={pattern.severity} />
            <Badge tone={detected ? 'accent' : 'neutral'}>{detected ? 'Detected' : humanizeCode(pattern.status)}</Badge>
          </div>
        }
      />

      <Card className="space-y-3 p-6">
        <h2 className="text-base font-semibold">{detected ? 'Why this pattern was detected' : 'Why this was not treated as a finding'}</h2>
        {detected && display.explanation && <p className="text-sm">{display.explanation}</p>}
        <p className="text-sm text-ink-soft">{patternReason(pattern.reason)}.</p>
        <dl className="grid gap-4 pt-2 sm:grid-cols-4">
          <div>
            <dt className="text-xs text-ink-soft">Category</dt>
            <dd className="text-sm">{pattern.category === 'CROSS_DIMENSIONAL' ? 'Cross-dimensional' : names.dimension(pattern.category).name}</dd>
          </div>
          <div>
            <dt className="text-xs text-ink-soft">Severity (impact)</dt>
            <dd className="text-sm">{humanizeCode(pattern.severity)}</dd>
          </div>
          <div>
            <dt className="text-xs text-ink-soft">Confidence (evidence quality)</dt>
            <dd className="text-sm">
              <ConfidenceIndicator band={pattern.confidenceBand} value={pattern.confidence} />
            </dd>
          </div>
          <div>
            <dt className="text-xs text-ink-soft">Affected dimensions</dt>
            <dd className="text-sm">{affected.length ? affected.map((a) => names.dimension(a).name).join(', ') : 'None directly'}</dd>
          </div>
        </dl>
      </Card>

      <Card>
        <CardHeader title="Matched conditions" description="Each condition and whether it was met" />
        <div className="p-5">
          {pattern.conditionResult ? <ConditionTree condition={pattern.conditionResult} names={names} /> : <p className="text-sm text-ink-soft">No condition detail was recorded.</p>}
          {(pattern.missingOperands?.length ?? 0) > 0 && (
            <p className="mt-3 text-sm text-ink-soft">Missing data: {pattern.missingOperands?.map((o) => humanizeCode(o.split(':')[1] ?? o)).join(', ')}.</p>
          )}
        </div>
      </Card>

      <Card>
        <CardHeader title="Evidence" description={`${pattern.evidence.length} ${pattern.evidence.length === 1 ? 'item' : 'items'}`} />
        <div className="space-y-4 p-5">
          <EvidenceList evidence={pattern.evidence} names={names} redacted={d.redacted} />
          {pattern.confidenceBreakdown && (
            <details className="text-sm">
              <summary className="cursor-pointer text-ink-soft hover:text-ink">How confidence was determined</summary>
              <dl className="mt-2 grid gap-x-6 gap-y-1 sm:grid-cols-2">
                {Object.entries(pattern.confidenceBreakdown).map(([k, v]) => (
                  <div key={k} className="flex justify-between gap-3">
                    <dt className="text-ink-soft">{k === 'respondents' ? 'Respondents (fewest among the supporting questions)' : humanizeCode(k)}</dt>
                    <dd className="tabular-nums">{breakdownValue(k, v)}</dd>
                  </div>
                ))}
              </dl>
              <p className="mt-2 text-xs text-ink-faint">Confidence reflects the quality of the evidence. It is separate from severity, which reflects impact. Both are configured, unvalidated defaults.</p>
            </details>
          )}
        </div>
      </Card>

      <div className="grid gap-8 lg:grid-cols-2">
        <Card>
          <CardHeader title="Associated penalties" description="The scoring consequence of this pattern, if any" />
          <div className="space-y-3 p-5">
            {penalties.length === 0 ? (
              <p className="text-sm text-ink-soft">No penalty is configured for this pattern.</p>
            ) : (
              penalties.map((p) => (
                <div key={`${p.penaltyId}-${p.version}`} className="text-sm">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <Link className="font-medium underline-offset-2 hover:underline" to={`/runs/${runId}/penalties/${p.penaltyId}`}>
                      {names.penalty(p.penaltyId, p.code).name}
                    </Link>
                    <PenaltyStateBadge status={p.status} />
                  </div>
                  <p className="text-xs text-ink-soft">
                    Requested {formatScore(p.candidateDeduction)}, effective {formatScore(p.effectiveDeduction)}. {penaltyReason(p.reason)}.
                  </p>
                </div>
              ))
            )}
          </div>
        </Card>
        <Card>
          <CardHeader title="Recommended actions" />
          <div className="space-y-3 p-5">
            {actions.length === 0 ? (
              <p className="text-sm text-ink-soft">No recommended actions are configured for this pattern.</p>
            ) : (
              actions.map((r) => (
                <div key={r.key}>
                  <p className="text-sm font-semibold">{r.title}</p>
                  <p className="text-sm text-ink-soft">{r.description}</p>
                </div>
              ))
            )}
          </div>
        </Card>
      </div>

      <Card>
        <CardHeader title="Related patterns" description="Detected in the same category or resting on the same evidence" />
        <div className="p-5">
          {related.length === 0 ? (
            <p className="text-sm text-ink-soft">No related patterns were detected.</p>
          ) : (
            <ul className="divide-y divide-line text-sm">
              {related.map((p) => (
                <li key={p.patternId} className="flex flex-wrap items-center justify-between gap-2 py-2">
                  <Link className="font-medium underline-offset-2 hover:underline" to={`/runs/${runId}/patterns/${p.patternId}`}>
                    {names.pattern(p.patternId, p.code).name}
                  </Link>
                  <SeverityBadge severity={p.severity} />
                </li>
              ))}
            </ul>
          )}
        </div>
      </Card>
    </div>
  );
}
