import { DimensionCard } from '@/components/dimensions/DimensionCard';
import type { Comparison, ScoringResult } from '@/types/api';
import type { DimensionDetail, Pattern, Penalty } from '@/types/views';
import { DIMENSIONS } from '@/utils/labels';
import type { Names } from './display';
import { dimensionRisk } from './selectors';

interface Props {
  result: ScoringResult;
  details: DimensionDetail[];
  patterns: Pattern[];
  penalties: Penalty[];
  names: Names;
  /** adjusted score of each dimension per assessment, oldest first, as returned by the trends endpoint */
  history: Record<string, (number | null | undefined)[]>;
  comparison?: Comparison;
  redacted: boolean;
}

export function DimensionOverview({ result, details, patterns, penalties, names, history, comparison, redacted }: Props) {
  return (
    <section aria-labelledby="dimensions-heading">
      <h2 id="dimensions-heading" className="mb-3 text-lg font-semibold">
        Dimensions
      </h2>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {DIMENSIONS.map((key) => (
          <DimensionCard
            key={key}
            dimension={names.dimension(key)}
            result={result.dimensions?.[key]}
            detail={details.find((d) => d.score.dimension === key)}
            names={names}
            history={history[key] ?? []}
            change={comparison?.comparable ? comparison.dimensions?.find((d) => d.dimension === key)?.change : undefined}
            risk={dimensionRisk(key, patterns, penalties)}
            redacted={redacted}
            detailHref={result.scoringRunId ? `/runs/${result.scoringRunId}/dimensions/${key}` : undefined}
          />
        ))}
      </div>
    </section>
  );
}
