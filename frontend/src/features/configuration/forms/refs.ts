import { getIn, isRecord } from './path';

export const DIMENSIONS = ['TEAM_HEALTH', 'DELIVERY_STABILITY', 'TECHNICAL_RISK', 'LEADERSHIP_LOAD'];
export const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
export const PATTERN_CATEGORIES = [...DIMENSIONS, 'CROSS_DIMENSIONAL'];
export const MISSING_POLICIES = ['IGNORE', 'NEUTRAL', 'RISK_SIGNAL', 'REQUIRED'];
export const OUT_OF_RANGE = ['REJECT', 'CLAMP'];
export const DIRECTIONS = ['POSITIVE', 'NEGATIVE'];
export const UNKNOWN_OPTION = ['REJECT', 'TREAT_AS_MISSING'];
export const GAP_DIRECTIONS = ['LEFT_MINUS_RIGHT', 'RIGHT_MINUS_LEFT'];

const list = (doc: unknown, path: (string | number)[]): Record<string, unknown>[] => {
  const v = getIn(doc, path);
  return Array.isArray(v) ? v.filter(isRecord) : [];
};

export const questionIds = (doc: unknown) => list(doc, ['questions']).map((q) => String(q.id));
export const signalIds = (doc: unknown) => list(doc, ['signals']).map((s) => String(s.id));
export const recommendationKeys = (doc: unknown) => list(doc, ['recommendations']).map((r) => String(r.key));

/** Every operand a condition tree names, as "signal:ID", "question:ID" or "dimension:NAME". */
export function operandsOf(condition: unknown): string[] {
  if (!isRecord(condition)) return [];
  const own: string[] = [];
  for (const kind of ['signal', 'dimension', 'question'] as const) {
    if (typeof condition[kind] === 'string') own.push(`${kind}:${condition[kind]}`);
  }
  for (const side of ['left', 'right']) own.push(...operandsOf(condition[side]));
  own.push(...operandsOf(condition.condition));
  if (Array.isArray(condition.conditions)) condition.conditions.forEach((c) => own.push(...operandsOf(c)));
  return own;
}

/** What still depends on a question: mappings, signals and conditions. Empty means it can be removed without leaving a dangling reference. */
export function usesOfQuestion(doc: unknown, id: string): string[] {
  const uses: string[] = [];
  list(doc, ['dimensions', 'mappings']).forEach((m) => m.question === id && uses.push(`a dimension mapping (${String(m.dimension)})`));
  list(doc, ['signals']).forEach((s) => Array.isArray(s.sources) && s.sources.includes(id) && uses.push(`signal ${String(s.id)}`));
  list(doc, ['patterns']).forEach((p) => operandsOf(p.condition).includes(`question:${id}`) && uses.push(`pattern ${String(p.id)}`));
  return uses;
}

/** What still depends on a signal: pattern conditions. */
export function usesOfSignal(doc: unknown, id: string): string[] {
  return list(doc, ['patterns']).filter((p) => operandsOf(p.condition).includes(`signal:${id}`)).map((p) => `pattern ${String(p.id)}`);
}

/** The first identifier not yet taken: PREFIX01, PREFIX02, ... */
export function nextId(prefix: string, taken: string[]): string {
  for (let n = 1; ; n++) {
    const id = `${prefix}${String(n).padStart(2, '0')}`;
    if (!taken.includes(id)) return id;
  }
}
