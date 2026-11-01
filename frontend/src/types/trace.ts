import { z } from 'zod';
import { dimensionDetailSchema, imbalanceSchema, patternSchema, penaltySchema, UnexpectedResponseError } from './views';

// The calculation trace stored with a run (decision D24), one object per pipeline stage. The frontend renders it and
// never recomputes anything from it. Stages the caller may not see (a viewer gets no normalization or signal stage)
// are simply absent.

const num = z.number().nullish();

const normalizedInput = z.looseObject({
  questionId: z.string(),
  questionType: z.string(),
  status: z.string(),
  normalizedValue: num,
  clamped: z.boolean().nullish(),
  strategyId: z.string().nullish(),
  detectedSignals: z.array(z.string()).default([]),
});

const signal = z.looseObject({
  signalId: z.string(),
  dataType: z.string(),
  numericValue: num,
  flag: z.boolean().nullish(),
  reliability: num,
  sourceQuestionIds: z.array(z.string()).default([]),
});

const adjustedDimension = z.looseObject({
  dimension: z.string(),
  rawScore: num,
  dimensionPenalty: num,
  adjustedScore: num,
  weight: num,
  weightedContribution: num,
});

const band = z.looseObject({ label: z.string(), rangeMin: num, rangeMax: num });

const header = z.looseObject({
  scoreStatus: z.string(),
  engineVersion: z.string(),
  algorithmVersion: z.string(),
  configurationVersion: z.string(),
  roundingPolicyVersion: z.string().nullish(),
  tieBreakVersion: z.string().nullish(),
  confidenceAlgorithmVersion: z.string().nullish(),
});

const stages = z.array(z.looseObject({ stage: z.string() }));
const root = z.looseObject({ traceVersion: z.string(), header, steps: stages });

export type NormalizedInput = z.infer<typeof normalizedInput>;
export type TraceSignal = z.infer<typeof signal>;
export type AdjustedDimension = z.infer<typeof adjustedDimension>;
export type TraceBand = z.infer<typeof band>;

export interface Trace {
  traceVersion: string;
  header: z.infer<typeof header>;
  normalization?: NormalizedInput[];
  signals?: TraceSignal[];
  rawDimensions: z.infer<typeof dimensionDetailSchema>[];
  imbalances: z.infer<typeof imbalanceSchema>[];
  patterns: z.infer<typeof patternSchema>[];
  penalties: z.infer<typeof penaltySchema>[];
  effectiveByTarget: Record<string, number>;
  adjusted: AdjustedDimension[];
  dimensionLabels: Record<string, TraceBand>;
  overall?: {
    status: string;
    weightedAdjustedScore?: number | null;
    overallPenaltyApplied?: number | null;
    finalScore?: number | null;
    dimensions: AdjustedDimension[];
  };
  interpretation?: TraceBand | null;
}

function stage<T>(steps: { stage: string }[], name: string, schema: z.ZodType<T>, pick: (raw: Record<string, unknown>) => unknown): T | undefined {
  const raw = steps.find((s) => s.stage === name) as Record<string, unknown> | undefined;
  if (!raw) return undefined;
  const parsed = schema.safeParse(pick(raw));
  if (!parsed.success) throw new UnexpectedResponseError('explanation');
  return parsed.data;
}

export function parseTrace(value: unknown): Trace {
  const parsed = root.safeParse(value);
  if (!parsed.success) throw new UnexpectedResponseError('explanation');
  const steps = parsed.data.steps;
  const list = <T>(name: string, schema: z.ZodType<T>, pick: (r: Record<string, unknown>) => unknown) =>
    stage(steps, name, z.array(schema), pick);
  const overallStage = stage(
    steps,
    'OVERALL',
    z.looseObject({
      overall: z.looseObject({
        status: z.string(),
        weightedAdjustedScore: num,
        overallPenaltyApplied: num,
        finalScore: num,
        dimensions: z.array(adjustedDimension).default([]),
      }),
      interpretation: band.nullish(),
    }),
    (r) => r,
  );
  const penaltyStage = stage(
    steps,
    'PENALTIES',
    z.looseObject({ aggregation: z.looseObject({ results: z.array(penaltySchema).default([]), effectiveByTarget: z.record(z.string(), z.number()).default({}) }) }),
    (r) => r,
  );
  const adjustedStage = stage(
    steps,
    'ADJUSTED_DIMENSIONS',
    z.looseObject({ labels: z.record(z.string(), band).default({}), dimensions: z.array(adjustedDimension).default([]) }),
    (r) => r,
  );
  return {
    traceVersion: parsed.data.traceVersion,
    header: parsed.data.header,
    normalization: list('NORMALIZATION', normalizedInput, (r) => r['inputs']),
    signals: list('SIGNALS', signal, (r) => r['signals']),
    rawDimensions: list('RAW_DIMENSIONS', dimensionDetailSchema, (r) => r['dimensions']) ?? [],
    imbalances: list('IMBALANCES', imbalanceSchema, (r) => r['imbalances']) ?? [],
    patterns: list('PATTERNS', patternSchema, (r) => r['patterns']) ?? [],
    penalties: penaltyStage?.aggregation.results ?? [],
    effectiveByTarget: penaltyStage?.aggregation.effectiveByTarget ?? {},
    adjusted: adjustedStage?.dimensions ?? [],
    dimensionLabels: adjustedStage?.labels ?? {},
    overall: overallStage?.overall,
    interpretation: overallStage?.interpretation,
  };
}
