import type { Tone } from '@/components/ui/Badge';
import { humanizeCode } from './presentation';

export const DIMENSIONS = ['TEAM_HEALTH', 'DELIVERY_STABILITY', 'TECHNICAL_RISK', 'LEADERSHIP_LOAD'] as const;
export type DimensionKey = (typeof DIMENSIONS)[number];

/** Most severe first. */
export const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const;
export type SeverityKey = (typeof SEVERITIES)[number];

export const CONFIDENCE_BANDS = ['STRONG', 'MODERATE', 'WEAK', 'INSUFFICIENT'] as const;

export function severityRank(severity: string | null | undefined): number {
  const index = SEVERITIES.indexOf(severity as SeverityKey);
  return index === -1 ? SEVERITIES.length : index;
}

export const severityTone = (severity: string | null | undefined): Tone =>
  severity === 'CRITICAL' ? 'critical' : severity === 'HIGH' ? 'high' : severity === 'MEDIUM' ? 'medium' : severity === 'LOW' ? 'low' : 'neutral';

/**
 * Colour defaults for interpretation labels (frontend-spec 6.2). They only choose a colour for a label the backend
 * returned; they never classify a score.
 */
export function interpretationTone(label: string | null | undefined): Tone {
  switch (label) {
    case 'STRONG':
    case 'HEALTHY':
      return 'ok';
    case 'NEEDS_ATTENTION':
      return 'medium';
    case 'AT_RISK':
      return 'high';
    case 'CRITICAL':
      return 'critical';
    default:
      return 'neutral';
  }
}

const COMPARABILITY: Record<string, string> = {
  CONFIGURATION_DIFFERS: 'the scoring configuration changed',
  ALGORITHM_VERSION_DIFFERS: 'the scoring algorithm version changed',
  TEMPLATE_VERSION_DIFFERS: 'the assessment template version changed',
  TEMPLATE_DIFFERS: 'the assessments use different templates',
  ORGANIZATION_DIFFERS: 'the assessments belong to different organizations',
};

export const comparabilityReason = (code: string): string => COMPARABILITY[code] ?? humanizeCode(code).toLowerCase();

const PENALTY_REASONS: Record<string, string> = {
  APPLIED_IN_FULL: 'Applied in full',
  APPLIED_PARTIALLY_CAPPED: 'Applied, reduced by a cap',
  SUPPRESSED_EXCLUSIVE_GROUP: 'Suppressed: another penalty in the same exclusive group won',
  SUPPRESSED_MAX: 'Suppressed: a larger penalty applies instead',
  SUPPRESSED_MAX_PER_CATEGORY: 'Suppressed: a larger penalty in the same category applies instead',
  SUPPRESSED_PRIORITY: 'Suppressed: a higher-priority penalty applies instead',
  SUPPRESSED_CUMULATIVE_CAP: 'Suppressed: the cumulative cap was reached',
  PATTERN_NOT_MATCHED: 'The triggering pattern was not detected',
  PATTERN_INELIGIBLE: 'The triggering pattern lacks enough evidence to penalize',
  PATTERN_NOT_PENALTY_ELIGIBLE: 'The triggering pattern is not configured to penalize',
  CONFIDENCE_BELOW_PENALTY_MINIMUM: 'Confidence is below the minimum required to penalize',
  PATTERN_NOT_EVALUATED: 'The triggering pattern could not be evaluated',
  PENALTY_DISABLED: 'The penalty is disabled in the configuration',
  BELOW_THRESHOLD: 'Below the configured threshold',
  ZERO_DEDUCTION: 'The calculated deduction is zero',
};

export const penaltyReason = (code: string): string => PENALTY_REASONS[code] ?? humanizeCode(code);

const PATTERN_REASONS: Record<string, string> = {
  CONDITION_SATISFIED: 'All conditions were met',
  CONDITION_NOT_SATISFIED: 'The conditions were not met',
  MISSING_SIGNALS: 'Some required signals were missing',
  PATTERN_DISABLED: 'The pattern is disabled in the configuration',
  INSUFFICIENT_INDEPENDENT_SIGNALS: 'Not enough independent signals support it',
  CONFIDENCE_BELOW_MINIMUM: 'Confidence is below the configured minimum',
};

export const patternReason = (code: string): string => PATTERN_REASONS[code] ?? humanizeCode(code);

export const PENALTY_STATES = ['APPLIED', 'SUPPRESSED', 'INELIGIBLE', 'NOT_TRIGGERED'] as const;
export type PenaltyState = (typeof PENALTY_STATES)[number];
