import { Link } from 'react-router-dom';
import { SegmentTrack } from '@/components/explanations/StackedBar';
import { Waterfall } from '@/components/explanations/Waterfall';
import { PenaltyStateBadge } from '@/components/penalties/PenaltyStateBadge';
import { SeverityBadge } from '@/components/patterns/SeverityBadge';
import { Badge } from '@/components/ui/Badge';
import type { Names } from '@/features/dashboard/display';
import type { Trace } from '@/types/trace';
import { interpretationTone } from '@/utils/labels';
import { formatRatio, formatScore, humanizeCode } from '@/utils/presentation';

interface StageProps {
  trace: Trace;
  names: Names;
  runId: string;
}

const th = 'py-1.5 pr-4 text-left text-xs font-medium text-ink-soft';
const td = 'py-1.5 pr-4 align-top tabular-nums';

function Hidden({ what }: { what: string }) {
  return <p className="text-sm text-ink-soft">{what} is hidden for your role. The later steps, which are what the score depends on, are still shown in full.</p>;
}

export function InputsStage({ trace, names }: StageProps) {
  if (!trace.normalization) return <Hidden what="The list of answered questions" />;
  return (
    <div>
      <p className="mb-2 text-sm text-ink-soft">
        {trace.normalization.length} questions were part of this assessment. The answers themselves are not part of the calculation trace.
      </p>
      <ul className="grid gap-2 sm:grid-cols-2">
        {trace.normalization.map((q) => (
          <li key={q.questionId} className="rounded-lg border border-line px-3 py-2 text-sm">
            <span className="font-medium">{names.question(q.questionId).name}</span>
            <span className="ml-2 text-xs text-ink-faint">{humanizeCode(q.questionType)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function NormalizationStage({ trace, names }: StageProps) {
  if (!trace.normalization) return <Hidden what="Normalization detail" />;
  return (
    <div className="overflow-x-auto">
      <p className="mb-2 text-sm text-ink-soft">Every answer is converted to a common 0&ndash;100 scale where 100 is the desirable end. Text answers raise signals instead of a value.</p>
      <table className="w-full min-w-max text-sm">
        <caption className="sr-only">Normalized answers</caption>
        <thead>
          <tr className="border-b border-line">
            <th scope="col" className={th}>Question</th>
            <th scope="col" className={th}>Type</th>
            <th scope="col" className={th}>Normalized value</th>
            <th scope="col" className={th}>Outcome</th>
            <th scope="col" className={th}>Signals raised</th>
          </tr>
        </thead>
        <tbody>
          {trace.normalization.map((q) => (
            <tr key={q.questionId} className="border-b border-line/60">
              <th scope="row" className="py-1.5 pr-4 text-left font-normal">{names.question(q.questionId).name}</th>
              <td className="py-1.5 pr-4">{humanizeCode(q.questionType)}</td>
              <td className={td}>{formatScore(q.normalizedValue)}</td>
              <td className="py-1.5 pr-4">
                {humanizeCode(q.status)}
                {q.clamped && <Badge tone="medium">Clamped to range</Badge>}
              </td>
              <td className="py-1.5 pr-4">{q.detectedSignals.length ? q.detectedSignals.map((s) => names.signal(s).name).join(', ') : '\u2013'}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {trace.signals && trace.signals.length > 0 && (
        <div className="mt-5">
          <h4 className="mb-1 text-sm font-medium">Signals derived from the answers</h4>
          <ul className="divide-y divide-line rounded-lg border border-line text-sm">
            {trace.signals.map((s) => (
              <li key={s.signalId} className="flex flex-wrap justify-between gap-2 px-3 py-2">
                <span>
                  {names.signal(s.signalId).name}
                  <span className="ml-2 text-xs text-ink-faint">from {s.sourceQuestionIds.map((q) => names.question(q).name).join(', ')}</span>
                </span>
                <span className="tabular-nums text-ink-soft">
                  {s.numericValue != null ? formatScore(s.numericValue) : s.flag != null ? (s.flag ? 'Present' : 'Absent') : '\u2013'}
                  <span className="ml-2 text-xs text-ink-faint">reliability {formatRatio(s.reliability)}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export function ContributionsStage({ trace, names }: StageProps) {
  const dims = trace.rawDimensions;
  const hidden = dims.every((d) => d.score.contributions.length === 0) && dims.length > 0;
  return (
    <div className="space-y-5">
      <p className="text-sm text-ink-soft">Each dimension is the weighted average of the questions mapped to it. Weights come from the configuration.</p>
      {hidden && <Hidden what="Question-level contributions" />}
      {dims.map((d) => (
        <div key={d.score.dimension} className="overflow-x-auto">
          <h4 className="mb-1 text-sm font-medium">{names.dimension(d.score.dimension).name}</h4>
          {d.score.status !== 'VALID' ? (
            <p className="text-sm text-ink-soft">Not enough data to score this dimension (coverage {formatRatio(d.score.coverage?.coverage)}, {formatRatio(d.score.coverage?.requiredCoverage)} required). No score is estimated.</p>
          ) : d.score.contributions.length > 0 ? (
            <table className="w-full min-w-max text-sm">
              <caption className="sr-only">Contributions to {names.dimension(d.score.dimension).name}</caption>
              <thead>
                <tr className="border-b border-line">
                  <th scope="col" className={th}>Question</th>
                  <th scope="col" className={th}>Value</th>
                  <th scope="col" className={th}>Weight</th>
                  <th scope="col" className={th}>Share of weight</th>
                  <th scope="col" className={th}>Contribution</th>
                </tr>
              </thead>
              <tbody>
                {d.score.contributions.map((c) => (
                  <tr key={c.questionId} className="border-b border-line/60">
                    <th scope="row" className="py-1.5 pr-4 text-left font-normal">{names.question(c.questionId).name}</th>
                    <td className={td}>{formatScore(c.value)}</td>
                    <td className={td}>{c.weight ?? '\u2013'}</td>
                    <td className={td}>{formatRatio(c.normalizedWeight)}</td>
                    <td className={td}>{formatScore(c.contribution)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : null}
        </div>
      ))}
    </div>
  );
}

export function RawScoresStage({ trace, names, runId }: StageProps) {
  return (
    <div>
      <p className="mb-3 text-sm text-ink-soft">Raw dimension scores, before any penalty. Patterns and imbalances are detected from these raw scores.</p>
      <ul className="space-y-3">
        {trace.rawDimensions.map((d) => (
          <li key={d.score.dimension} className="grid grid-cols-[10rem_1fr_4.5rem] items-center gap-3 text-sm">
            <Link className="truncate underline-offset-2 hover:underline" to={`/runs/${runId}/dimensions/${d.score.dimension}`}>
              {names.dimension(d.score.dimension).name}
            </Link>
            <SegmentTrack label={`${names.dimension(d.score.dimension).name} raw score ${formatScore(d.score.rawScore)}`} segments={[{ label: 'Raw score', value: d.score.rawScore, className: 'bg-accent' }]} />
            <span className="text-right tabular-nums">{d.score.status === 'VALID' ? formatScore(d.score.rawScore) : 'No data'}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function PatternsStage({ trace, names, runId }: StageProps) {
  const detected = trace.patterns.filter((p) => p.status === 'MATCHED');
  return (
    <div>
      <p className="mb-3 text-sm text-ink-soft">
        {detected.length} of {trace.patterns.length} configured patterns were detected. A pattern is an organizational condition; it changes no score by itself.
      </p>
      {detected.length === 0 ? (
        <p className="text-sm text-ink-soft">No organizational patterns were detected.</p>
      ) : (
        <ul className="divide-y divide-line rounded-lg border border-line">
          {detected.map((p) => (
            <li key={p.patternId} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm">
              <Link className="font-medium underline-offset-2 hover:underline" to={`/runs/${runId}/patterns/${p.patternId}`}>
                {names.pattern(p.patternId, p.code).name}
              </Link>
              <span className="flex items-center gap-3">
                <SeverityBadge severity={p.severity} />
                <span className="text-xs text-ink-soft">Confidence {humanizeCode(p.confidenceBand)}</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function PenaltiesStage({ trace, names, runId }: StageProps) {
  const relevant = trace.penalties.filter((p) => p.status !== 'NOT_TRIGGERED');
  const totals = Object.entries(trace.effectiveByTarget).filter(([, v]) => v > 0);
  return (
    <div>
      <p className="mb-3 text-sm text-ink-soft">
        Penalties are the scoring consequence of a detected pattern, after eligibility and the configured stacking policy. Suppressed penalties are listed too: they were requested but changed nothing.
      </p>
      {relevant.length === 0 ? (
        <p className="text-sm text-ink-soft">No penalty was requested.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-max text-sm">
            <caption className="sr-only">Penalties</caption>
            <thead>
              <tr className="border-b border-line">
                <th scope="col" className={th}>Penalty</th>
                <th scope="col" className={th}>State</th>
                <th scope="col" className={th}>Target</th>
                <th scope="col" className={th}>Requested</th>
                <th scope="col" className={th}>Effective</th>
                <th scope="col" className={th}>Suppressed</th>
              </tr>
            </thead>
            <tbody>
              {relevant.map((p) => (
                <tr key={`${p.penaltyId}-${p.version}`} className="border-b border-line/60">
                  <th scope="row" className="py-1.5 pr-4 text-left font-normal">
                    <Link className="underline-offset-2 hover:underline" to={`/runs/${runId}/penalties/${p.penaltyId}`}>
                      {names.penalty(p.penaltyId, p.code).name}
                    </Link>
                  </th>
                  <td className="py-1.5 pr-4"><PenaltyStateBadge status={p.status} /></td>
                  <td className="py-1.5 pr-4">{p.target === 'OVERALL' ? 'Overall score' : names.dimension(p.target).name}</td>
                  <td className={td}>{formatScore(p.candidateDeduction)}</td>
                  <td className={td}>{formatScore(p.effectiveDeduction)}</td>
                  <td className={td}>{formatScore(p.suppressedAmount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {totals.length > 0 && (
        <p className="mt-3 text-sm text-ink-soft">
          Effective deductions after aggregation: {totals.map(([t, v]) => `${t === 'OVERALL' ? 'overall score' : names.dimension(t).name} ${formatScore(v)}`).join(', ')}.
        </p>
      )}
    </div>
  );
}

export function AdjustedStage({ trace, names }: StageProps) {
  return (
    <div className="overflow-x-auto">
      <p className="mb-2 text-sm text-ink-soft">Adjusted score = raw score minus the effective dimension penalty (never below zero).</p>
      <table className="w-full min-w-max text-sm">
        <caption className="sr-only">Raw to adjusted dimension scores</caption>
        <thead>
          <tr className="border-b border-line">
            <th scope="col" className={th}>Dimension</th>
            <th scope="col" className={th}>Raw</th>
            <th scope="col" className={th}>Penalty</th>
            <th scope="col" className={th}>Adjusted</th>
            <th scope="col" className={th}>Label</th>
            <th scope="col" className={th}>Weight</th>
            <th scope="col" className={th}>Weighted contribution</th>
          </tr>
        </thead>
        <tbody>
          {trace.adjusted.map((d) => {
            const label = trace.dimensionLabels[d.dimension]?.label;
            return (
              <tr key={d.dimension} className="border-b border-line/60">
                <th scope="row" className="py-1.5 pr-4 text-left font-normal">{names.dimension(d.dimension).name}</th>
                <td className={td}>{formatScore(d.rawScore)}</td>
                <td className={td}>{formatScore(d.dimensionPenalty)}</td>
                <td className={`${td} font-semibold`}>{formatScore(d.adjustedScore)}</td>
                <td className="py-1.5 pr-4">{label ? <Badge tone={interpretationTone(label)}>{humanizeCode(label)}</Badge> : '\u2013'}</td>
                <td className={td}>{d.weight ?? '\u2013'}</td>
                <td className={td}>{formatScore(d.weightedContribution)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export function FinalStage({ trace, names }: StageProps) {
  const overall = trace.overall;
  if (!overall || overall.status !== 'VALID') {
    return <p className="text-sm text-ink-soft">The score could not be calculated because there was not enough data. No score is shown rather than an estimate.</p>;
  }
  return (
    <div className="space-y-4">
      <Waterfall
        dimensions={overall.dimensions}
        names={(d) => names.dimension(d).name}
        weightedAdjustedScore={overall.weightedAdjustedScore}
        overallPenalty={overall.overallPenaltyApplied}
        finalScore={overall.finalScore}
      />
      <p className="flex flex-wrap items-center gap-3 border-t border-line pt-4">
        <span className="text-sm text-ink-soft">Final score</span>
        <span className="text-3xl font-semibold tabular-nums">{formatScore(overall.finalScore)}</span>
        {trace.interpretation && <Badge tone={interpretationTone(trace.interpretation.label)}>{humanizeCode(trace.interpretation.label)}</Badge>}
        {trace.interpretation && (
          <span className="text-xs text-ink-faint">
            band {trace.interpretation.rangeMin}&ndash;{trace.interpretation.rangeMax}
          </span>
        )}
      </p>
    </div>
  );
}

export const STAGES = [
  { id: 'inputs', title: 'Assessment inputs', Body: InputsStage },
  { id: 'normalization', title: 'Normalization', Body: NormalizationStage },
  { id: 'contributions', title: 'Dimension contributions', Body: ContributionsStage },
  { id: 'raw', title: 'Raw dimension scores', Body: RawScoresStage },
  { id: 'patterns', title: 'Pattern detection', Body: PatternsStage },
  { id: 'penalties', title: 'Penalties', Body: PenaltiesStage },
  { id: 'adjusted', title: 'Adjusted dimensions', Body: AdjustedStage },
  { id: 'final', title: 'Final LIS', Body: FinalStage },
] as const;
