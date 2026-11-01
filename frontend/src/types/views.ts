import { z } from 'zod';

// The pattern, penalty, imbalance and dimension parts of the API are free-form JSON in the OpenAPI document. They are
// validated here, once, at the boundary: a response in an unexpected shape becomes a clear error instead of a blank
// screen. Fields are lenient (extra fields are kept) because the backend adds fields over time.

const num = z.number().nullish();
const text = z.string().nullish();

export interface ConditionNode {
  operator: string;
  outcome: string;
  detail?: string | null;
  children?: ConditionNode[] | null;
}

const conditionNode: z.ZodType<ConditionNode> = z.lazy(() =>
  z.object({
    operator: z.string(),
    outcome: z.string(),
    detail: text,
    children: z.array(conditionNode).nullish(),
  }),
);

export const evidenceSchema = z.looseObject({
  kind: z.string(),
  operandKey: z.string(),
  numericValue: num,
  // boolean signals report whether they are present
  flag: z.union([z.boolean(), z.string()]).nullish(),
  reliability: num,
  correlationGroup: text,
  sourceQuestionIds: z.array(z.string()).nullish(),
});

export const patternSchema = z.looseObject({
  patternId: z.string(),
  code: z.string(),
  version: z.number(),
  category: z.string(),
  severity: z.string(),
  status: z.string(),
  reason: z.string(),
  confidence: num,
  confidenceBand: text,
  penaltyEligible: z.boolean().nullish(),
  actionKeys: z.array(z.string()).default([]),
  evidence: z.array(evidenceSchema).default([]),
  conditionResult: conditionNode.nullish(),
  missingOperands: z.array(z.string()).nullish(),
  confidenceBreakdown: z.record(z.string(), z.unknown()).nullish(),
});

export const penaltySchema = z.looseObject({
  penaltyId: z.string(),
  code: z.string(),
  version: z.number(),
  status: z.string(),
  reason: z.string(),
  target: z.string(),
  strategy: z.string(),
  category: text,
  stackingPolicy: z.string(),
  triggerPatternId: z.string(),
  triggerPatternVersion: num,
  candidateDeduction: z.number(),
  effectiveDeduction: z.number(),
  suppressedAmount: z.number(),
  winningPenaltyId: text,
  exclusiveGroup: text,
  priority: num,
  ruleOrder: num,
  scoreBase: text,
  scoreBaseValue: num,
  evidenceRefs: z.array(z.string()).default([]),
  explanationKey: text,
});

export const imbalanceSchema = z.looseObject({
  ruleId: z.string(),
  status: z.string(),
  severity: z.string(),
  left: z.string(),
  right: z.string(),
  gap: num,
  threshold: num,
  leftValue: num,
  rightValue: num,
  reason: z.string(),
  recommendationKey: text,
  explanationKey: text,
});

const contributionSchema = z.looseObject({
  questionId: z.string(),
  status: z.string(),
  value: num,
  weight: num,
  normalizedWeight: num,
  contribution: num,
});

export const dimensionDetailSchema = z.looseObject({
  score: z.looseObject({
    dimension: z.string(),
    status: z.string(),
    rawScore: num,
    coverage: z.looseObject({ status: z.string(), coverage: num, requiredCoverage: num }).nullish(),
    contributions: z.array(contributionSchema).default([]),
  }),
  positiveDrivers: z.array(z.unknown()).default([]),
  negativeDrivers: z.array(z.unknown()).default([]),
});

/** How the answers of one question were combined (decision D45). It never carries an answer. */
export const responseSummarySchema = z.looseObject({
  questionId: z.string(),
  scope: z.string(),
  method: z.string(),
  respondents: num,
  participation: z.string(),
  usedAnswers: z.number(),
  minimum: z.number(),
  belowMinimum: z.boolean(),
});

const responsesSchema = z.looseObject({
  questions: z.array(responseSummarySchema).default([]),
  unscoredTeams: z.array(z.string()).default([]),
});

export type ResponseSummary = z.infer<typeof responseSummarySchema>;
export type Responses = z.infer<typeof responsesSchema>;

export type Evidence = z.infer<typeof evidenceSchema>;
export type Pattern = z.infer<typeof patternSchema>;
export type Penalty = z.infer<typeof penaltySchema>;
export type Imbalance = z.infer<typeof imbalanceSchema>;
export type DimensionDetail = z.infer<typeof dimensionDetailSchema>;

export class UnexpectedResponseError extends Error {
  constructor(what: string) {
    super(`The ${what} response was not in the expected format`);
    this.name = 'UnexpectedResponseError';
  }
}

function parseList<T>(schema: z.ZodType<T>, value: unknown, what: string): T[] {
  if (value === undefined || value === null) return [];
  const parsed = z.array(schema).safeParse(value);
  if (!parsed.success) throw new UnexpectedResponseError(what);
  return parsed.data;
}

export const parsePatterns = (value: unknown) => parseList(patternSchema, value, 'patterns');
export const parsePenalties = (value: unknown) => parseList(penaltySchema, value, 'penalties');
export const parseImbalances = (value: unknown) => parseList(imbalanceSchema, value, 'imbalances');
export const parseDimensionDetails = (value: unknown) => parseList(dimensionDetailSchema, value, 'dimensions');

/** Null when the run was scored from one answer per question, which has no summaries. */
export function parseResponses(value: unknown): Responses | null {
  if (value === undefined || value === null) return null;
  const parsed = responsesSchema.safeParse(value);
  if (!parsed.success) throw new UnexpectedResponseError('response summaries');
  return parsed.data;
}

export function parseEffectiveByTarget(value: unknown): Record<string, number> {
  const parsed = z.record(z.string(), z.number()).safeParse(value ?? {});
  if (!parsed.success) throw new UnexpectedResponseError('penalty totals');
  return parsed.data;
}
