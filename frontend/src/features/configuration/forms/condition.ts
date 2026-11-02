import { isRecord } from './path';

export type Node = Record<string, unknown>;
export type OperandKind = 'signal' | 'dimension' | 'question';

export interface Targets {
  signals: string[];
  questions: string[];
  dimensions: string[];
}

export const GROUPS = ['AND', 'OR', 'NOT'];
export const COMPARE = ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE'];
export const OTHER = ['BETWEEN', 'IN', 'NOT_IN', 'EXISTS', 'NOT_EXISTS', 'CONTAINS', 'GAP_GT', 'GAP_GTE'];

export const OP_WORDS: Record<string, string> = {
  AND: 'All of these',
  OR: 'Any of these',
  NOT: 'None of these (not)',
  EQ: 'equals',
  NE: 'does not equal',
  GT: 'is above',
  GTE: 'is at least',
  LT: 'is below',
  LTE: 'is at most',
  BETWEEN: 'is between',
  IN: 'is one of',
  NOT_IN: 'is none of',
  EXISTS: 'has a value',
  NOT_EXISTS: 'has no value',
  CONTAINS: 'question has signal',
  GAP_GT: 'gap is above',
  GAP_GTE: 'gap is at least',
};

export type OpKind = 'group' | 'compare' | 'between' | 'list' | 'exists' | 'contains' | 'gap';

export function opKind(op: string): OpKind {
  if (GROUPS.includes(op)) return 'group';
  if (COMPARE.includes(op)) return 'compare';
  if (op === 'BETWEEN') return 'between';
  if (op === 'IN' || op === 'NOT_IN') return 'list';
  if (op === 'EXISTS' || op === 'NOT_EXISTS') return 'exists';
  if (op === 'CONTAINS') return 'contains';
  return 'gap';
}

export const isGroupNode = (n: Node) => GROUPS.includes(String(n.operator));

export function operandKind(o: unknown): OperandKind {
  if (isRecord(o) && typeof o.dimension === 'string') return 'dimension';
  if (isRecord(o) && typeof o.question === 'string') return 'question';
  return 'signal';
}

export function operandTarget(o: unknown): string {
  if (!isRecord(o)) return '';
  return String(o[operandKind(o)] ?? '');
}

export function makeOperand(kind: OperandKind, target: string): Node {
  return { [kind]: target };
}

const firstTarget = (kind: OperandKind, t: Targets) => (kind === 'dimension' ? t.dimensions : kind === 'question' ? t.questions : t.signals)[0] ?? '';

/** A comparison of the first signal, the starting point of any new condition. */
export function defaultLeaf(t: Targets, op = 'LTE'): Node {
  return convert({ operator: 'LTE', signal: t.signals[0] ?? '', value: 25 }, op, t);
}

/** The operand of a leaf: the one it names, or the left side of a gap. */
function mainOperand(n: Node, t: Targets): Node {
  if (isRecord(n.left)) return n.left;
  for (const kind of ['signal', 'dimension', 'question'] as const) if (typeof n[kind] === 'string') return { [kind]: n[kind] };
  return makeOperand('signal', firstTarget('signal', t));
}

/**
 * Changing the operator converts the condition and keeps what still applies: the operand, the value and, between groups, the
 * conditions inside.
 */
export function convert(n: Node, op: string, t: Targets): Node {
  const kind = opKind(op);
  if (kind === 'group') {
    const inner: Node[] = Array.isArray(n.conditions) ? (n.conditions as Node[]) : isRecord(n.condition) ? [n.condition as Node] : isGroupNode(n) ? [] : [n];
    if (op === 'NOT') return { operator: op, condition: inner[0] ?? defaultLeaf(t) };
    return { operator: op, conditions: inner.length > 0 ? inner : [defaultLeaf(t)] };
  }
  const operand = isGroupNode(n) ? makeOperand('signal', firstTarget('signal', t)) : mainOperand(n, t);
  const value = typeof n.value === 'number' || typeof n.value === 'boolean' ? n.value : typeof n.threshold === 'number' ? n.threshold : 25;
  switch (kind) {
    case 'compare':
      return { operator: op, ...operand, value };
    case 'between':
      return { operator: op, ...operand, low: typeof n.low === 'number' ? n.low : 0, high: typeof n.high === 'number' ? n.high : 100 };
    case 'list':
      return { operator: op, ...operand, values: Array.isArray(n.values) ? n.values : [typeof value === 'number' ? value : 25] };
    case 'exists':
      return { operator: op, ...operand };
    case 'contains':
      return { operator: op, question: typeof n.question === 'string' ? n.question : firstTarget('question', t), signal: typeof n.signal === 'string' ? n.signal : firstTarget('signal', t) };
    default:
      return {
        operator: op,
        left: operand,
        right: isRecord(n.right) ? n.right : makeOperand('signal', t.signals[1] ?? firstTarget('signal', t)),
        direction: typeof n.direction === 'string' ? n.direction : 'LEFT_MINUS_RIGHT',
        value: typeof value === 'number' ? value : 30,
      };
  }
}

/** A short sentence for a condition, for the summary line of a pattern. */
export function describe(n: unknown): string {
  if (!isRecord(n)) return '';
  const op = String(n.operator);
  const kind = opKind(op);
  const who = (o: unknown) => operandTarget(o);
  if (kind === 'group') {
    if (op === 'NOT') return `not (${describe(n.condition)})`;
    return `(${((n.conditions as unknown[]) ?? []).map(describe).join(op === 'AND' ? ' and ' : ' or ')})`;
  }
  if (kind === 'gap') return `${who(n.left)} ${OP_WORDS[op]} ${String(n.value)} from ${who(n.right)}`;
  if (kind === 'contains') return `${String(n.question)} has ${String(n.signal)}`;
  const target = who(n);
  if (kind === 'between') return `${target} is between ${String(n.low)} and ${String(n.high)}`;
  if (kind === 'list') return `${target} ${OP_WORDS[op]} ${((n.values as unknown[]) ?? []).join(', ')}`;
  if (kind === 'exists') return `${target} ${OP_WORDS[op]}`;
  return `${target} ${OP_WORDS[op]} ${String(n.value)}`;
}
