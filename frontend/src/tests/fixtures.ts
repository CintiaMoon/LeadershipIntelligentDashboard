// Test-only data in the shape the backend returns (captured from the real API). Never imported by production code.
import type { Comparison, Dashboard, Recommendations, RunCatalog, RunSummary, ScoringResult, Trends } from '@/types/api';

export const RUN_ID = '11111111-1111-1111-1111-111111111111';
export const PREVIOUS_RUN_ID = '00000000-0000-0000-0000-000000000000';

export const lisBands = [
  { label: 'CRITICAL', min: 0, max: 40 },
  { label: 'AT_RISK', min: 40, max: 60 },
  { label: 'HEALTHY', min: 60, max: 80 },
  { label: 'STRONG', min: 80, max: 100 },
];

export const summary: RunSummary = {
  scoringRunId: RUN_ID,
  assessmentId: 'asm-1',
  organizationId: 'org-1',
  templateId: 'tpl-1',
  templateVersion: 1,
  source: 'CALCULATE',
  leadershipIntelligenceScore: 67,
  interpretation: 'HEALTHY',
  configurationVersion: 'platform_default:v1',
  configurationHash: 'sha256:aaa',
  algorithmVersion: '1.0.0',
  engineVersion: '3.0.0',
  calculatedAt: '2026-11-01T09:00:00Z',
  previousScoringRunId: PREVIOUS_RUN_ID,
  previousScore: 62.5,
  change: 4.5,
  comparable: true,
  notComparableReasons: [],
};

export const dashboard: Dashboard = {
  organizationId: 'org-1',
  latestPerTemplate: [summary],
  recentRuns: [summary],
  assessmentsByStatus: { COMPLETED: 2 },
  notice: 'The Leadership Intelligence Score is an organizational diagnostic signal. It is not an individual performance rating.',
};

export const run: ScoringResult = {
  scoringRunId: RUN_ID,
  assessmentId: 'asm-1',
  organizationId: 'org-1',
  status: 'COMPLETED',
  scoreStatus: 'VALID',
  leadershipIntelligenceScore: 67,
  interpretation: 'HEALTHY',
  engineVersion: '3.0.0',
  algorithmVersion: '1.0.0',
  configurationVersion: 'platform_default:v1',
  configurationHash: 'sha256:aaa',
  calculatedAt: '2026-11-01T09:00:00Z',
  notice: dashboard.notice,
  dimensions: {
    TEAM_HEALTH: { status: 'VALID', rawScore: 25, effectivePenalty: 12, adjustedScore: 13, coverage: 1, weight: 0.25, label: 'CRITICAL' },
    DELIVERY_STABILITY: { status: 'VALID', rawScore: 75, effectivePenalty: 0, adjustedScore: 75, coverage: 1, weight: 0.25, label: 'HEALTHY' },
    TECHNICAL_RISK: { status: 'VALID', rawScore: 100, effectivePenalty: 0, adjustedScore: 100, coverage: 1, weight: 0.25, label: 'STRONG' },
    LEADERSHIP_LOAD: { status: 'VALID', rawScore: 80, effectivePenalty: 0, adjustedScore: 80, coverage: 1, weight: 0.25, label: 'STRONG' },
  },
  overall: { weightedAdjustedScore: 67, effectiveOverallPenalty: 0, finalScore: 67 },
};

const contribution = (dimension: string, questionId: string, value: number) => ({
  value,
  status: 'APPLIED',
  weight: 1,
  dimension,
  questionId,
  contribution: value,
  normalizedWeight: 1,
});

const detail = (dimension: string, rawScore: number, questionId: string) => ({
  score: {
    status: 'VALID',
    coverage: { status: 'SUFFICIENT', coverage: 1, requiredCoverage: 0.75 },
    rawScore,
    dimension,
    contributions: [contribution(dimension, questionId, rawScore)],
  },
  negativeDrivers: [],
  positiveDrivers: [],
});

export const dimensionsResponse = {
  scoringRunId: RUN_ID,
  redacted: false,
  dimensions: [detail('TEAM_HEALTH', 25, 'Q01'), detail('DELIVERY_STABILITY', 75, 'Q02'), detail('TECHNICAL_RISK', 100, 'Q03'), detail('LEADERSHIP_LOAD', 80, 'Q05')],
  adjusted: [],
};

export const redactedDimensionsResponse = {
  ...dimensionsResponse,
  redacted: true,
  dimensions: dimensionsResponse.dimensions.map((d) => ({ score: { ...d.score, contributions: undefined } })),
};

const evidence = (operandKey: string, numericValue: number | undefined, question: string) => ({
  kind: 'SIGNAL',
  operandKey,
  reliability: 1,
  numericValue,
  correlationGroup: operandKey,
  sourceQuestionIds: numericValue === undefined ? undefined : [question],
});

const pattern = (patternId: string, code: string, category: string, severity: string, status: string, band: string, confidence: number, actionKeys: string[], values = true) => ({
  patternId,
  code,
  version: 1,
  category,
  severity,
  status,
  reason: status === 'MATCHED' ? 'CONDITION_SATISFIED' : 'CONFIDENCE_BELOW_MINIMUM',
  confidence,
  confidenceBand: band,
  penaltyEligible: status === 'MATCHED',
  actionKeys,
  evidence: [evidence('signal:WORKLOAD_PRESSURE', values ? 80 : undefined, 'Q05'), evidence('signal:TEAM_SUSTAINABILITY', values ? 25 : undefined, 'Q01')],
  missingOperands: [],
});

export const patternsResponse = {
  scoringRunId: RUN_ID,
  redacted: false,
  patterns: [
    pattern('TH-001', 'TEAM_BURNOUT_RISK', 'TEAM_HEALTH', 'CRITICAL', 'MATCHED', 'STRONG', 1, ['REVIEW_TEAM_CAPACITY', 'REVIEW_WORKLOAD_DISTRIBUTION']),
    pattern('DS-001', 'DELIVERY_SLIP', 'DELIVERY_STABILITY', 'HIGH', 'MATCHED', 'MODERATE', 0.7, ['STABILIZE_DELIVERY']),
    pattern('XD-001', 'LOAD_SPILLOVER', 'CROSS_DIMENSIONAL', 'MEDIUM', 'MATCHED', 'WEAK', 0.5, []),
    pattern('TR-001', 'RELEASE_RISK', 'TECHNICAL_RISK', 'LOW', 'INELIGIBLE', 'INSUFFICIENT', 0.2, []),
    pattern('LL-001', 'LEADER_OVERLOAD', 'LEADERSHIP_LOAD', 'HIGH', 'NOT_MATCHED', 'STRONG', 1, []),
  ],
};

export const redactedPatternsResponse = {
  ...patternsResponse,
  redacted: true,
  patterns: [pattern('TH-001', 'TEAM_BURNOUT_RISK', 'TEAM_HEALTH', 'CRITICAL', 'MATCHED', 'STRONG', 1, ['REVIEW_TEAM_CAPACITY'], false)],
};

const penalty = (penaltyId: string, code: string, status: string, target: string, trigger: string, candidate: number, effective: number, suppressed: number, reason: string, extra = {}) => ({
  penaltyId,
  code,
  version: 1,
  status,
  reason,
  target,
  strategy: 'SEVERITY_BASED',
  category: 'SUSTAINABILITY',
  stackingPolicy: 'MAX_PER_CATEGORY',
  triggerPatternId: trigger,
  candidateDeduction: candidate,
  effectiveDeduction: effective,
  suppressedAmount: suppressed,
  evidenceRefs: [],
  ...extra,
});

export const penaltiesResponse = {
  scoringRunId: RUN_ID,
  redacted: false,
  tieBreakVersion: '1',
  effectiveByTarget: { OVERALL: 0, TEAM_HEALTH: 12, TECHNICAL_RISK: 0, LEADERSHIP_LOAD: 0, DELIVERY_STABILITY: 0 },
  penalties: [
    penalty('TH-P01', 'BURNOUT_RISK_PENALTY', 'APPLIED', 'TEAM_HEALTH', 'TH-001', 12, 12, 0, 'APPLIED_IN_FULL'),
    penalty('DS-P01', 'DELIVERY_SLIP_PENALTY', 'SUPPRESSED', 'DELIVERY_STABILITY', 'DS-001', 8, 0, 8, 'SUPPRESSED_MAX_PER_CATEGORY', { winningPenaltyId: 'DS-P02' }),
    penalty('TR-P01', 'RELEASE_RISK_PENALTY', 'INELIGIBLE', 'TECHNICAL_RISK', 'TR-001', 0, 0, 0, 'PATTERN_INELIGIBLE'),
    penalty('XD-P01', 'SPILLOVER_PENALTY', 'NOT_TRIGGERED', 'OVERALL', 'XD-001', 0, 0, 0, 'BELOW_THRESHOLD'),
  ],
};

export const recommendations: Recommendations = {
  scoringRunId: RUN_ID,
  recommendations: [
    { key: 'REVIEW_TEAM_CAPACITY', title: 'Review team capacity', description: 'Compare the planned workload with available capacity.', priority: 10, severity: 'CRITICAL', sources: [{ type: 'PATTERN', id: 'TH-001', severity: 'CRITICAL' }] },
    { key: 'STABILIZE_DELIVERY', title: 'Stabilize delivery', description: 'Reduce work in progress.', priority: 20, severity: 'HIGH', sources: [{ type: 'PATTERN', id: 'DS-001', severity: 'HIGH' }] },
  ],
  unmappedActionKeys: ['REVIEW_WORKLOAD_DISTRIBUTION'],
  notice: dashboard.notice,
};

export const catalog: RunCatalog = {
  scoringRunId: RUN_ID,
  lisBands,
  dimensionBands: lisBands,
  patterns: {
    'TH-001': { name: 'Team burnout risk', description: 'Sustained workload pressure with weak sustainability.', explanation: 'High workload and low sustainability were reported together.' },
    'DS-001': { name: 'Delivery slippage', description: 'Delivery repeatedly misses its commitments.' },
    'XD-001': { name: 'Load spillover', description: 'Leadership load spills into delivery.' },
    'TR-001': { name: 'Release risk', description: 'Releases carry elevated risk.' },
  },
  penalties: {
    'TH-P01': { name: 'Burnout risk deduction', description: 'Lowers Team Health when burnout risk is detected.', explanation: 'Team Health is lowered because burnout risk was detected.' },
    'DS-P01': { name: 'Delivery slippage deduction', description: 'Lowers Delivery Stability.' },
    'DS-P02': { name: 'Stronger delivery deduction', description: 'A larger delivery deduction.' },
  },
  dimensions: {
    TEAM_HEALTH: { name: 'Team Health', description: 'How healthy and sustainable the team environment is.' },
    DELIVERY_STABILITY: { name: 'Delivery Stability', description: 'How predictable delivery is.' },
    TECHNICAL_RISK: { name: 'Technical Risk', description: 'How well technical risk is controlled.' },
    LEADERSHIP_LOAD: { name: 'Leadership Load', description: 'How manageable the load on leaders is.' },
  },
  signals: { WORKLOAD_PRESSURE: { name: 'Workload pressure', description: '' }, TEAM_SUSTAINABILITY: { name: 'Team sustainability', description: '' } },
  questions: { Q01: { name: 'Sustainable pace', description: '' }, Q02: { name: 'Delivery predictability', description: '' }, Q03: { name: 'Technical safeguards', description: '' }, Q05: { name: 'Workload level', description: '' } },
};

const point = (id: string, at: string, score: number, comparable: boolean, reasons: string[], team: number, matched: string[], effective: number, suppressed: number, previous?: string) => ({
  run: {
    ...summary,
    scoringRunId: id,
    calculatedAt: at,
    leadershipIntelligenceScore: score,
    comparable,
    notComparableReasons: reasons,
    previousScoringRunId: previous,
  },
  dimensions: [
    { dimension: 'TEAM_HEALTH', rawScore: team, adjustedScore: team },
    { dimension: 'DELIVERY_STABILITY', rawScore: 75, adjustedScore: 75 },
    { dimension: 'TECHNICAL_RISK', rawScore: 100, adjustedScore: 100 },
    { dimension: 'LEADERSHIP_LOAD', rawScore: 80, adjustedScore: 80 },
  ],
  patterns: matched.map((patternId) => ({ patternId, severity: patternId === 'TH-001' ? 'CRITICAL' : 'HIGH', category: 'TEAM_HEALTH' })),
  patternsBySeverity: { CRITICAL: matched.includes('TH-001') ? 1 : 0, HIGH: matched.includes('DS-001') ? 1 : 0, MEDIUM: 0, LOW: 0 },
  penalties: { applied: effective > 0 ? 1 : 0, suppressed: suppressed > 0 ? 1 : 0, ineligible: 0, notTriggered: 0, effectiveDeduction: effective, suppressedAmount: suppressed },
});

export const trends: Trends = {
  organizationId: 'org-1',
  templateId: 'tpl-1',
  notice: dashboard.notice,
  points: [
    point('r0', '2026-09-01T09:00:00Z', 55, false, [], 20, ['TH-001', 'DS-001'], 12, 8),
    point('r1', '2026-10-01T09:00:00Z', 62.5, true, [], 40, ['TH-001'], 12, 0, 'r0'),
    point('r2', '2026-11-01T09:00:00Z', 67, false, ['CONFIGURATION_DIFFERS'], 13, ['TH-001'], 12, 0, 'r1'),
  ],
} as Trends;

export const comparison: Comparison = {
  base: { ...summary, scoringRunId: PREVIOUS_RUN_ID },
  run: summary,
  comparable: true,
  notComparableReasons: [],
  scoreChange: 4.5,
  dimensions: [
    { dimension: 'TEAM_HEALTH', baseScore: 8.5, score: 13, change: 4.5 },
    { dimension: 'DELIVERY_STABILITY', baseScore: 75, score: 75, change: 0 },
  ],
  patterns: { appeared: [], resolved: [], persistent: ['TH-001'] },
  notice: dashboard.notice,
};


// ---------------------------------------------------------------- explanation trace and answers

const adjustedRows = [
  { dimension: 'TEAM_HEALTH', rawScore: 25, dimensionPenalty: 12, adjustedScore: 13, weight: 0.25, weightedContribution: 3.25 },
  { dimension: 'DELIVERY_STABILITY', rawScore: 75, dimensionPenalty: 0, adjustedScore: 75, weight: 0.25, weightedContribution: 18.75 },
  { dimension: 'TECHNICAL_RISK', rawScore: 100, dimensionPenalty: 0, adjustedScore: 100, weight: 0.25, weightedContribution: 25 },
  { dimension: 'LEADERSHIP_LOAD', rawScore: 80, dimensionPenalty: 0, adjustedScore: 80, weight: 0.25, weightedContribution: 20 },
];

const fullSteps = [
  {
    stage: 'NORMALIZATION',
    inputs: [
      { status: 'NORMALIZED', clamped: false, questionId: 'Q01', strategyId: 'scale', questionType: 'SCALE', detectedSignals: [], normalizedValue: 25, strategyVersion: '1' },
      { status: 'NORMALIZED', clamped: true, questionId: 'Q05', strategyId: 'number', questionType: 'NUMBER', detectedSignals: [], normalizedValue: 80, strategyVersion: '1' },
      { status: 'SIGNALS_ONLY', clamped: false, questionId: 'Q04', strategyId: 'text', questionType: 'TEXT', detectedSignals: ['WORKLOAD_PRESSURE'], normalizedValue: null, strategyVersion: '1' },
    ],
  },
  {
    stage: 'SIGNALS',
    signals: [
      { flag: null, dataType: 'NUMBER', signalId: 'WORKLOAD_PRESSURE', reliability: 1, numericValue: 80, derivationVersion: '1', sourceQuestionIds: ['Q05'] },
      { flag: true, dataType: 'BOOLEAN', signalId: 'TEAM_SUSTAINABILITY', reliability: 0.8, numericValue: null, derivationVersion: '1', sourceQuestionIds: ['Q01'] },
    ],
  },
];

const restSteps = (withContributions: boolean) => [
  { stage: 'RAW_DIMENSIONS', dimensions: withContributions ? dimensionsResponse.dimensions : redactedDimensionsResponse.dimensions },
  { stage: 'IMBALANCES', imbalances: [] },
  { stage: 'PATTERNS', patterns: patternsResponse.patterns },
  { stage: 'PENALTIES', aggregation: { results: penaltiesResponse.penalties, tieBreakVersion: '1', effectiveByTarget: penaltiesResponse.effectiveByTarget } },
  {
    stage: 'ADJUSTED_DIMENSIONS',
    labels: { TEAM_HEALTH: { label: 'CRITICAL', rangeMin: 0, rangeMax: 40 }, DELIVERY_STABILITY: { label: 'HEALTHY', rangeMin: 60, rangeMax: 80 } },
    dimensions: adjustedRows,
  },
  {
    stage: 'OVERALL',
    overall: { status: 'VALID', dimensions: adjustedRows, finalScore: 67, overallPenaltyApplied: 0, weightedAdjustedScore: 67 },
    interpretation: { label: 'HEALTHY', rangeMin: 60, rangeMax: 80 },
  },
];

const header = {
  scoreStatus: 'VALID',
  engineVersion: '3.0.0',
  tieBreakVersion: '1',
  algorithmVersion: '1.0.0',
  configurationVersion: 'platform_default:v1',
  roundingPolicyVersion: '1',
};

export const traceResponse = { scoringRunId: RUN_ID, redacted: false, trace: { traceVersion: '1', header, steps: [...fullSteps, ...restSteps(true)] } };

/** What a viewer receives: no normalization or signal stage, no contributions. */
export const redactedTraceResponse = { scoringRunId: RUN_ID, redacted: true, trace: { traceVersion: '1', header, steps: restSteps(false) } };

export const answersResponse = {
  scoringRunId: RUN_ID,
  answers: [
    { questionId: 'Q01', answer: 2 },
    { questionId: 'Q02', answer: 'OFTEN' },
    { questionId: 'Q03', answer: true },
    { questionId: 'Q05', answer: 80 },
  ],
};

export const runsPage = {
  items: [
    { ...summary, scoringRunId: RUN_ID },
    { ...summary, scoringRunId: PREVIOUS_RUN_ID, leadershipIntelligenceScore: 62.5, calculatedAt: '2026-10-01T09:00:00Z', previousScoringRunId: undefined },
  ],
};

export const incomparable: Comparison = {
  base: { ...summary, scoringRunId: PREVIOUS_RUN_ID, configurationVersion: 'platform_default:v1', configurationHash: 'sha256:aaa', templateVersion: 1 },
  run: { ...summary, configurationVersion: 'platform_default:v2', configurationHash: 'sha256:bbb', templateVersion: 2 },
  comparable: false,
  notComparableReasons: ['CONFIGURATION_DIFFERS', 'TEMPLATE_VERSION_DIFFERS'],
  scoreChange: undefined,
  dimensions: [{ dimension: 'TEAM_HEALTH', baseScore: 8.5, score: 13, change: undefined }],
  patterns: { appeared: ['DS-001'], resolved: [], persistent: ['TH-001'] },
  notice: dashboard.notice,
};
