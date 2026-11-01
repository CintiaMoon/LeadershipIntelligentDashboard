import { useMemo } from 'react';
import { useCatalog, usePatterns, usePenalties, useRecommendations, useRun, useDimensions } from '@/services/queries/hooks';
import { parseDimensionDetails, parseEffectiveByTarget, parsePatterns, parsePenalties } from '@/types/views';
import { makeNames } from '@/features/dashboard/display';

/** Everything a detail page needs about one stored run, validated at the boundary. */
export function useRunDetails(runId: string | undefined) {
  const run = useRun(runId);
  const catalog = useCatalog(runId);
  const dimensions = useDimensions(runId);
  const patterns = usePatterns(runId);
  const penalties = usePenalties(runId);
  const recommendations = useRecommendations(runId);
  const names = useMemo(() => makeNames(catalog.data), [catalog.data]);
  const parsed = useMemo(() => {
    try {
      return {
        details: parseDimensionDetails(dimensions.data?.dimensions),
        patterns: parsePatterns(patterns.data?.patterns),
        penalties: parsePenalties(penalties.data?.penalties),
        effectiveByTarget: parseEffectiveByTarget(penalties.data?.effectiveByTarget),
        error: null as Error | null,
      };
    } catch (e) {
      return { details: [], patterns: [], penalties: [], effectiveByTarget: {}, error: e as Error };
    }
  }, [dimensions.data, patterns.data, penalties.data]);
  const redacted = patterns.data?.redacted ?? true;
  const dimensionsRedacted = dimensions.data?.redacted ?? true;
  return { run, catalog, dimensions, patterns, penalties, recommendations, names, parsed, redacted, dimensionsRedacted };
}
