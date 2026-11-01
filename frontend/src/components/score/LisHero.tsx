import { Link } from 'react-router-dom';
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { Sparkline } from '@/components/charts/Sparkline';
import type { RunCatalog, RunSummary, ScoringResult } from '@/types/api';
import { interpretationTone } from '@/utils/labels';
import { formatBound, formatDateTime, formatScore, humanizeCode } from '@/utils/presentation';
import { ComparisonNote } from './ComparisonNote';
import { ScoreGauge } from './ScoreGauge';

interface Props {
  result: ScoringResult;
  summary?: RunSummary;
  bands: NonNullable<RunCatalog['lisBands']>;
  history: (number | null | undefined)[];
  templateId?: string;
}

/** The visual anchor of the dashboard: the LIS exactly as the backend returned it. */
export function LisHero({ result, summary, bands, history, templateId }: Props) {
  const label = result.interpretation;
  const band = bands.find((b) => b.label === label);
  const shown = history.filter((v): v is number => typeof v === 'number');
  const historyText = shown.length >= 2 ? `Score over the last ${shown.length} assessments, oldest to newest: ${shown.map(formatScore).join(', ')}.` : '';

  return (
    <Card className="p-6 sm:p-8" aria-labelledby="lis-heading">
      <div className="grid gap-8 lg:grid-cols-[auto_1fr]">
        <div>
          <h2 id="lis-heading" className="text-sm font-medium uppercase tracking-wide text-ink-soft">
            Leadership Intelligence Score
          </h2>
          <p className="mt-2 flex items-end gap-3">
            <span className="text-6xl font-semibold leading-none tracking-tight tabular-nums text-ink sm:text-7xl" data-testid="lis-value">
              {formatScore(result.leadershipIntelligenceScore)}
            </span>
            <span className="pb-1.5 text-sm text-ink-faint">out of 100</span>
          </p>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            {label && <Badge tone={interpretationTone(label)}>{humanizeCode(label)}</Badge>}
            {band && (
              <span className="text-xs text-ink-soft">
                Band {formatBound(band.min)}&ndash;{formatBound(band.max)}
              </span>
            )}
          </div>
          <p className="mt-3 text-xs text-ink-faint">
            Last assessment {formatDateTime(result.calculatedAt)}
            {templateId && <> &middot; template {templateId}</>}
          </p>
        </div>
        <div className="space-y-5">
          <ScoreGauge score={result.leadershipIntelligenceScore} label={label} bands={bands} />
          <ComparisonNote summary={summary} />
          <div className="max-w-md">
            <p className="mb-1 text-xs font-medium text-ink-soft">Trend</p>
            <Sparkline values={history} summary={historyText || 'Not enough assessments for a trend.'} />
            {historyText && <p className="mt-1 text-xs text-ink-faint">{historyText}</p>}
          </div>
        </div>
      </div>
      <div className="mt-6 flex flex-wrap items-center gap-x-6 gap-y-2 border-t border-line pt-4">
        {result.scoringRunId && (
          <Link className="text-sm font-medium text-accent underline-offset-2 hover:underline" to={`/runs/${result.scoringRunId}/explanation`}>
            How was this calculated?
          </Link>
        )}
        {summary?.previousScoringRunId && result.scoringRunId && (
          <Link className="text-sm font-medium text-accent underline-offset-2 hover:underline" to={`/compare?base=${summary.previousScoringRunId}&run=${result.scoringRunId}`}>
            Compare with the previous assessment
          </Link>
        )}
        <p className="text-xs text-ink-soft">{result.notice}</p>
      </div>
    </Card>
  );
}
