import type { QuestionnaireDocument, QuestionSpec, RuleSpec } from '@/types/api';

/**
 * The builder's document and the pure helpers around it (decisions D47, D50). Nothing here scores anything or decides whether a
 * questionnaire is valid: the backend checks it on every save and on publish, and the builder only shows what it reports.
 */

export type QuestionKind = NonNullable<QuestionSpec['kind']>;

export interface KindInfo {
  kind: QuestionKind;
  label: string;
  /** Free text is collected and never scored; every other kind can be mapped to a dimension. */
  scorable: boolean;
}

/** In the order the palette shows them. */
export const KINDS: KindInfo[] = [
  { kind: 'SINGLE_CHOICE', label: 'Single choice', scorable: true },
  { kind: 'MULTI_CHOICE', label: 'Multiple choice', scorable: true },
  { kind: 'RATING', label: 'Rating (1–5)', scorable: true },
  { kind: 'LIKERT', label: 'Likert scale', scorable: true },
  { kind: 'NPS', label: 'NPS (0–10)', scorable: true },
  { kind: 'FREE_TEXT', label: 'Free text', scorable: false },
  { kind: 'NUMERIC', label: 'Numeric', scorable: true },
  { kind: 'YES_NO', label: 'Yes / No', scorable: true },
];

export const kindLabel = (kind: QuestionKind | undefined): string => KINDS.find((k) => k.kind === kind)?.label ?? 'Question';

export const DIMENSIONS = ['TEAM_HEALTH', 'DELIVERY_STABILITY', 'TECHNICAL_RISK', 'LEADERSHIP_LOAD'] as const;

export const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;

/** A condition of a rule. The generated API type cannot express the recursion, so the builder has its own. */
export interface Cond {
  op: string;
  conditions?: Cond[];
  condition?: Cond;
  question?: string;
  dimension?: string;
  value?: number;
  low?: number;
  high?: number;
  values?: number[];
}

export const GROUP_OPS = ['AND', 'OR', 'NOT'] as const;
export const COMPARE_OPS = ['LTE', 'LT', 'GTE', 'GT', 'EQ', 'NE', 'BETWEEN', 'IN', 'NOT_IN', 'EXISTS', 'NOT_EXISTS'] as const;

export const OP_LABEL: Record<string, string> = {
  AND: 'All of these',
  OR: 'Any of these',
  NOT: 'None of these',
  LTE: 'is at most',
  LT: 'is below',
  GTE: 'is at least',
  GT: 'is above',
  EQ: 'equals',
  NE: 'does not equal',
  BETWEEN: 'is between',
  IN: 'is one of',
  NOT_IN: 'is none of',
  EXISTS: 'was answered',
  NOT_EXISTS: 'was not answered',
};

export const isGroup = (c: Cond): boolean => (GROUP_OPS as readonly string[]).includes(c.op);

export function emptyDocument(name = ''): QuestionnaireDocument {
  return { name, description: '', questions: [], rules: [] };
}

/** The next free question id in the scheme the server uses (Q01, Q02, ...), so rules can refer to a question before it is saved. */
export function nextQuestionId(questions: QuestionSpec[]): string {
  const used = new Set(questions.map((q) => q.id));
  let n = 1;
  for (const id of used) {
    const match = /^Q(\d+)$/.exec(id ?? '');
    if (match) n = Math.max(n, Number(match[1]) + 1);
  }
  for (;;) {
    const id = `Q${String(n).padStart(2, '0')}`;
    if (!used.has(id)) return id;
    n++;
  }
}

export const defaultOptions = (): QuestionSpec['options'] => [
  { key: 'O1', label: 'Option 1' },
  { key: 'O2', label: 'Option 2' },
];

/** A new question of a kind, with sensible starting values for that kind. */
export function newQuestion(kind: QuestionKind, questions: QuestionSpec[]): QuestionSpec {
  const base: QuestionSpec = { id: nextQuestionId(questions), kind, text: '', required: false, options: [], labels: [] };
  return { ...base, ...kindDefaults(kind) };
}

function kindDefaults(kind: QuestionKind): Partial<QuestionSpec> {
  switch (kind) {
    case 'SINGLE_CHOICE':
    case 'MULTI_CHOICE':
      return { options: defaultOptions() };
    case 'LIKERT':
      return { points: 5, higherIsBetter: true };
    case 'RATING':
    case 'NPS':
      return { higherIsBetter: true };
    case 'NUMERIC':
      return { min: 0, max: 10, higherIsBetter: true };
    case 'YES_NO':
      return { desirableAnswer: 'YES' };
    default:
      return {};
  }
}

/** Switching the kind keeps what still applies (text, help, required, dimension, weight) and resets what does not. */
export function changeKind(q: QuestionSpec, kind: QuestionKind): QuestionSpec {
  const scorable = KINDS.find((k) => k.kind === kind)?.scorable ?? false;
  const kept: QuestionSpec = {
    id: q.id,
    kind,
    title: q.title,
    text: q.text,
    help: q.help,
    required: q.required,
    dimension: scorable ? q.dimension : undefined,
    weight: scorable ? q.weight : undefined,
    options: [],
    labels: [],
  };
  const defaults = kindDefaults(kind);
  return { ...kept, ...defaults, options: kind === 'SINGLE_CHOICE' || kind === 'MULTI_CHOICE' ? (q.options && q.options.length >= 2 ? q.options : defaultOptions()) : [] };
}

export const newOptionKey = (options: NonNullable<QuestionSpec['options']>): string => {
  const used = new Set(options.map((o) => o.key));
  let n = options.length + 1;
  while (used.has(`O${n}`)) n++;
  return `O${n}`;
};

export function newRule(questions: QuestionSpec[]): RuleSpec {
  const first = questions.find((q) => q.kind !== 'FREE_TEXT');
  return {
    name: '',
    description: '',
    severity: 'MEDIUM',
    enabled: true,
    minimumSignals: 1,
    minimumConfidence: 0.5,
    condition: { op: 'LTE', question: first?.id, value: 25 } as RuleSpec['condition'],
  };
}

export interface Summary {
  questions: number;
  required: number;
  dimensions: number;
  scored: number;
  rules: number;
}

export function summarize(doc: QuestionnaireDocument): Summary {
  const questions = doc.questions ?? [];
  const covered = new Set(questions.filter((q) => q.kind !== 'FREE_TEXT' && q.dimension).map((q) => q.dimension));
  return {
    questions: questions.length,
    required: questions.filter((q) => q.required).length,
    dimensions: covered.size,
    scored: questions.filter((q) => q.kind !== 'FREE_TEXT' && q.dimension).length,
    rules: (doc.rules ?? []).length,
  };
}

export function moveItem<T>(items: T[], from: number, to: number): T[] {
  if (to < 0 || to >= items.length || from === to) return items;
  const copy = [...items];
  const [moved] = copy.splice(from, 1);
  copy.splice(to, 0, moved as T);
  return copy;
}

/** Whether the working copy differs from what was last saved (a plain structural comparison of the documents). */
export const sameDocument = (a: QuestionnaireDocument, b: QuestionnaireDocument): boolean => JSON.stringify(a) === JSON.stringify(b);

/** The state words the library shows. */
export const STATE_LABEL: Record<string, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  PUBLISHED_WITH_DRAFT: 'Published, newer draft',
  ARCHIVED: 'Archived',
};
