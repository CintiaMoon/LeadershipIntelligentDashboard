import type { ConditionNode } from '@/types/views';
import type { Names } from '@/features/dashboard/display';
import { formatScore, humanizeCode } from '@/utils/presentation';

export type Outcome = 'met' | 'not-met' | 'unknown';

export interface ConditionView {
  outcome: Outcome;
  /** a sentence an engineering leader can read; falls back to the backend's own detail text */
  text: string;
  operator: string;
  children: ConditionView[];
}

const OPERATORS: Record<string, string> = {
  GTE: 'is at least',
  GT: 'is above',
  LTE: 'is at most',
  LT: 'is below',
  EQ: 'is equal to',
  NE: 'is not equal to',
  BETWEEN: 'is between',
  IN: 'is one of',
  NOT_IN: 'is none of',
  CONTAINS: 'contains',
  GAP_GT: 'differs by more than',
  GAP_GTE: 'differs by at least',
};

const GROUPS: Record<string, string> = {
  AND: 'All of these must be met',
  OR: 'At least one of these must be met',
  NOT: 'None of these may be met',
};

function outcomeOf(value: string): Outcome {
  return value === 'TRUE' ? 'met' : value === 'FALSE' ? 'not-met' : 'unknown';
}

function operand(key: string, names: Names): string {
  const [kind, id] = key.split(':', 2);
  if (kind === 'signal' && id) return names.signal(id).name;
  if (kind === 'dimension' && id) return names.dimension(id).name;
  return humanizeCode(key);
}

/**
 * The backend writes each leaf as "signal:WORKLOAD_PRESSURE=80.0000000000 GTE 75". It is rewritten with display names
 * and a readable operator; anything that does not fit that shape is shown exactly as the backend wrote it.
 */
export function describeLeaf(node: ConditionNode, names: Names): string {
  const detail = node.detail ?? '';
  const match = /^(\S+?)=(\S+) ([A-Z_]+) (.+)$/.exec(detail);
  const words = OPERATORS[node.operator];
  if (!match || !words) return detail || humanizeCode(node.operator);
  const [, key, value, , threshold] = match;
  const shown = Number.isFinite(Number(value)) ? formatScore(Number(value)) : (value ?? '');
  return `${operand(key ?? '', names)} ${words} ${threshold} (it was ${shown})`;
}

export function toConditionView(node: ConditionNode, names: Names): ConditionView {
  const children = (node.children ?? []).map((c) => toConditionView(c, names));
  const group = GROUPS[node.operator];
  return {
    outcome: outcomeOf(node.outcome),
    operator: node.operator,
    text: group && children.length > 0 ? group : describeLeaf(node, names),
    children,
  };
}

export const OUTCOME_WORDS: Record<Outcome, string> = { met: 'Met', 'not-met': 'Not met', unknown: 'Could not be evaluated: data missing' };
