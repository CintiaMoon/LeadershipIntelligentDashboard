import { Button } from '@/components/ui/Button';
import { inputClass, selectClass } from '@/components/ui/Form';
import type { QuestionSpec } from '@/types/api';
import { humanizeCode } from '@/utils/presentation';
import { COMPARE_OPS, DIMENSIONS, GROUP_OPS, OP_LABEL, isGroup, type Cond } from './model';

interface Props {
  value: Cond;
  onChange: (next: Cond) => void;
  /** Called for a condition inside a group; the top-level condition cannot be removed. */
  onRemove?: () => void;
  questions: QuestionSpec[];
  idPrefix: string;
  readOnly: boolean;
  depth?: number;
}

const MAX_DEPTH = 4;

const numberOrUndefined = (text: string): number | undefined => (text.trim() === '' || Number.isNaN(Number(text)) ? undefined : Number(text));

function leafFor(questions: QuestionSpec[], op = 'LTE'): Cond {
  const first = questions.find((q) => q.kind !== 'FREE_TEXT');
  return { op, question: first?.id, value: 25 };
}

/** Switching the operator between kinds converts the condition without losing what still applies. */
function convert(value: Cond, op: string, questions: QuestionSpec[]): Cond {
  const toGroup = (GROUP_OPS as readonly string[]).includes(op);
  if (toGroup) {
    const children = value.conditions ?? (value.condition ? [value.condition] : isGroup(value) ? [] : [value]);
    if (op === 'NOT') return { op, condition: children[0] ?? leafFor(questions) };
    return { op, conditions: children.length > 0 ? children : [leafFor(questions)] };
  }
  if (isGroup(value)) return leafFor(questions, op);
  return { ...value, op };
}

/**
 * The condition of a rule (decision D50): a tree of all-of, any-of and none-of groups around comparisons of a question or of a
 * dimension. A question is compared by its normalized value, 0 to 100 with 100 desirable; a dimension by its raw score.
 */
export function ConditionEditor({ value, onChange, onRemove, questions, idPrefix, readOnly, depth = 0 }: Props) {
  const group = isGroup(value);
  const scorable = questions.filter((q) => q.kind !== 'FREE_TEXT');

  const header = (
    <div className="flex flex-wrap items-center gap-2">
      <label className="sr-only" htmlFor={`${idPrefix}-op`}>
        Condition type
      </label>
      <select
        id={`${idPrefix}-op`}
        className={`${selectClass} !mt-0 !min-h-9 !w-auto`}
        value={value.op}
        disabled={readOnly}
        onChange={(e) => onChange(convert(value, e.target.value, questions))}
      >
        {!group && (
          <optgroup label="Compare">
            {COMPARE_OPS.map((op) => (
              <option key={op} value={op}>
                {OP_LABEL[op]}
              </option>
            ))}
          </optgroup>
        )}
        <optgroup label="Combine">
          {GROUP_OPS.filter(() => depth < MAX_DEPTH || group).map((op) => (
            <option key={op} value={op}>
              {OP_LABEL[op]}
            </option>
          ))}
        </optgroup>
        {group && (
          <optgroup label="Compare">
            {COMPARE_OPS.map((op) => (
              <option key={op} value={op}>
                {OP_LABEL[op]}
              </option>
            ))}
          </optgroup>
        )}
      </select>
      {onRemove && !readOnly && (
        <Button variant="ghost" onClick={onRemove} aria-label="Remove this condition">
          Remove
        </Button>
      )}
    </div>
  );

  if (group) {
    const children = value.op === 'NOT' ? (value.condition ? [value.condition] : []) : (value.conditions ?? []);
    const setChildren = (next: Cond[]) => onChange(value.op === 'NOT' ? { op: 'NOT', condition: next[0] } : { op: value.op, conditions: next });
    return (
      <div className="space-y-2 rounded-lg border border-line bg-surface p-3">
        {header}
        <div className="space-y-2 border-l-2 border-line pl-3">
          {children.map((child, i) => (
            <ConditionEditor
              key={i}
              value={child}
              idPrefix={`${idPrefix}-${i}`}
              questions={questions}
              readOnly={readOnly}
              depth={depth + 1}
              onChange={(next) => setChildren(children.map((c, j) => (j === i ? next : c)))}
              onRemove={value.op === 'NOT' ? undefined : children.length > 1 ? () => setChildren(children.filter((_, j) => j !== i)) : undefined}
            />
          ))}
          {!readOnly && value.op !== 'NOT' && (
            <div className="flex gap-2">
              <Button onClick={() => setChildren([...children, leafFor(questions)])}>Add condition</Button>
              {depth + 1 < MAX_DEPTH && <Button onClick={() => setChildren([...children, { op: 'AND', conditions: [leafFor(questions)] }])}>Add group</Button>}
            </div>
          )}
        </div>
      </div>
    );
  }

  const operand = value.question ? `question:${value.question}` : value.dimension ? `dimension:${value.dimension}` : '';
  const needsValue = !['BETWEEN', 'IN', 'NOT_IN', 'EXISTS', 'NOT_EXISTS'].includes(value.op);
  return (
    <div className="space-y-2 rounded-lg border border-line bg-canvas/40 p-3">
      {header}
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <label className="sr-only" htmlFor={`${idPrefix}-operand`}>
          What is compared
        </label>
        <select
          id={`${idPrefix}-operand`}
          className={`${selectClass} !mt-0 !min-h-9 !w-auto max-w-full`}
          value={operand}
          disabled={readOnly}
          onChange={(e) => {
            const [kind, id] = e.target.value.split(':');
            onChange({ ...value, question: kind === 'question' ? id : undefined, dimension: kind === 'dimension' ? id : undefined });
          }}
        >
          {operand === '' && <option value="">Choose...</option>}
          <optgroup label="Question (0 to 100, 100 is desirable)">
            {scorable.map((q) => (
              <option key={q.id} value={`question:${q.id}`}>
                {q.id} {q.title || (q.text ? q.text.slice(0, 50) : '')}
              </option>
            ))}
          </optgroup>
          <optgroup label="Dimension (raw score)">
            {DIMENSIONS.map((d) => (
              <option key={d} value={`dimension:${d}`}>
                {humanizeCode(d)}
              </option>
            ))}
          </optgroup>
        </select>
        <span className="text-ink-soft">{OP_LABEL[value.op] ?? value.op}</span>
        {needsValue && (
          <>
            <label className="sr-only" htmlFor={`${idPrefix}-value`}>
              Value
            </label>
            <input id={`${idPrefix}-value`} type="number" className={`${inputClass} !mt-0 !w-24`} value={value.value ?? ''} readOnly={readOnly} onChange={(e) => onChange({ ...value, value: numberOrUndefined(e.target.value) })} />
          </>
        )}
        {value.op === 'BETWEEN' && (
          <>
            <label className="sr-only" htmlFor={`${idPrefix}-low`}>
              From
            </label>
            <input id={`${idPrefix}-low`} type="number" className={`${inputClass} !mt-0 !w-24`} value={value.low ?? ''} readOnly={readOnly} onChange={(e) => onChange({ ...value, low: numberOrUndefined(e.target.value) })} />
            <span className="text-ink-soft">and</span>
            <label className="sr-only" htmlFor={`${idPrefix}-high`}>
              To
            </label>
            <input id={`${idPrefix}-high`} type="number" className={`${inputClass} !mt-0 !w-24`} value={value.high ?? ''} readOnly={readOnly} onChange={(e) => onChange({ ...value, high: numberOrUndefined(e.target.value) })} />
          </>
        )}
        {(value.op === 'IN' || value.op === 'NOT_IN') && (
          <>
            <label className="sr-only" htmlFor={`${idPrefix}-values`}>
              Values, separated by commas
            </label>
            <input
              id={`${idPrefix}-values`}
              type="text"
              inputMode="decimal"
              placeholder="0, 25, 50"
              className={`${inputClass} !mt-0 !w-40`}
              defaultValue={(value.values ?? []).join(', ')}
              readOnly={readOnly}
              onChange={(e) =>
                onChange({
                  ...value,
                  values: e.target.value
                    .split(',')
                    .map((t) => numberOrUndefined(t))
                    .filter((n): n is number => n !== undefined),
                })
              }
            />
          </>
        )}
      </div>
    </div>
  );
}
