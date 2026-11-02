import { Button } from '@/components/ui/Button';
import { selectClass } from '@/components/ui/Form';
import { COMPARE, convert, defaultLeaf, GROUPS, makeOperand, OP_WORDS, OTHER, opKind, operandKind, operandTarget, type Node, type OperandKind, type Targets } from './condition';
import { NumberListField, SelectField } from './controls';
import { NumberField } from './NumberField';
import { GAP_DIRECTIONS } from './refs';

const MAX_DEPTH = 4;
const KINDS: OperandKind[] = ['signal', 'dimension', 'question'];
const KIND_WORDS: Record<OperandKind, string> = { signal: 'Signal', dimension: 'Dimension', question: 'Question' };

function Operand({ id, label, value, targets, onChange, disabled }: { id: string; label: string; value: unknown; targets: Targets; onChange: (next: Node) => void; disabled: boolean }) {
  const kind = operandKind(value);
  const options = kind === 'dimension' ? targets.dimensions : kind === 'question' ? targets.questions : targets.signals;
  const target = operandTarget(value);
  return (
    <div className="flex flex-wrap items-end gap-2">
      <SelectField id={`${id}-kind`} label={`${label} is a`} value={kind} options={KINDS} words={KIND_WORDS} disabled={disabled} onChange={(k) => onChange(makeOperand(k as OperandKind, (k === 'dimension' ? targets.dimensions : k === 'question' ? targets.questions : targets.signals)[0] ?? ''))} className="min-w-32" />
      <SelectField id={`${id}-target`} label={label} value={target} options={options} words={Object.fromEntries(options.map((o) => [o, o]))} disabled={disabled} onChange={(t) => onChange(makeOperand(kind, t))} className="min-w-48" />
    </div>
  );
}

/**
 * The condition of a pattern (specv3 13.4): all of, any of or none of a set of conditions, around comparisons of a signal, a
 * dimension score or a question, ranges, lists, presence, and the gap between two of them. It edits the same JSON the parser reads.
 */
export function ConfigConditionEditor({ value, onChange, onRemove, targets, idPrefix, disabled, depth = 0 }: { value: Node; onChange: (next: Node) => void; onRemove?: () => void; targets: Targets; idPrefix: string; disabled: boolean; depth?: number }) {
  const op = String(value.operator);
  const kind = opKind(op);
  const patch = (changes: Node) => onChange({ ...value, ...changes });

  const header = (
    <div className="flex flex-wrap items-end gap-2">
      <div className="min-w-48">
        <label className="sr-only" htmlFor={`${idPrefix}-op`}>
          Condition type
        </label>
        <select id={`${idPrefix}-op`} className={`${selectClass} !mt-0`} value={op} disabled={disabled} onChange={(e) => onChange(convert(value, e.target.value, targets))}>
          <optgroup label="Group">
            {(depth < MAX_DEPTH || kind === 'group' ? GROUPS : []).map((g) => (
              <option key={g} value={g}>
                {OP_WORDS[g]}
              </option>
            ))}
          </optgroup>
          <optgroup label="Compare">
            {[...COMPARE, ...OTHER].map((o) => (
              <option key={o} value={o}>
                {OP_WORDS[o]}
              </option>
            ))}
          </optgroup>
        </select>
      </div>
      {onRemove && !disabled && (
        <Button variant="ghost" onClick={onRemove} aria-label="Remove this condition">
          Remove
        </Button>
      )}
    </div>
  );

  if (kind === 'group') {
    const children: Node[] = op === 'NOT' ? (value.condition ? [value.condition as Node] : []) : ((value.conditions as Node[]) ?? []);
    const setChildren = (next: Node[]) => (op === 'NOT' ? patch({ condition: next[0] ?? defaultLeaf(targets) }) : patch({ conditions: next }));
    return (
      <div className="space-y-3 rounded-md border border-line p-3">
        {header}
        <ul className="space-y-3 border-l-2 border-line pl-3">
          {children.map((c, i) => (
            <li key={`${idPrefix}-${i}`}>
              <ConfigConditionEditor
                value={c}
                targets={targets}
                idPrefix={`${idPrefix}-${i}`}
                disabled={disabled}
                depth={depth + 1}
                onChange={(next) => setChildren(children.map((x, j) => (j === i ? next : x)))}
                onRemove={op !== 'NOT' && children.length > 1 ? () => setChildren(children.filter((_, j) => j !== i)) : undefined}
              />
            </li>
          ))}
        </ul>
        {op !== 'NOT' && !disabled && depth < MAX_DEPTH && (
          <Button onClick={() => setChildren([...children, defaultLeaf(targets)])}>Add a condition</Button>
        )}
      </div>
    );
  }

  const num = (key: string) => (typeof value[key] === 'number' ? (value[key] as number) : undefined);
  return (
    <div className="space-y-3 rounded-md border border-line p-3">
      {header}
      {kind === 'gap' ? (
        <div className="space-y-3">
          <Operand id={`${idPrefix}-left`} label="Left" value={value.left} targets={targets} disabled={disabled} onChange={(next) => patch({ left: next })} />
          <Operand id={`${idPrefix}-right`} label="Right" value={value.right} targets={targets} disabled={disabled} onChange={(next) => patch({ right: next })} />
          <div className="flex flex-wrap items-end gap-2">
            <SelectField id={`${idPrefix}-direction`} label="Gap measured as" value={String(value.direction ?? '')} options={GAP_DIRECTIONS} words={{ LEFT_MINUS_RIGHT: 'Left minus right', RIGHT_MINUS_LEFT: 'Right minus left' }} disabled={disabled} onChange={(v) => patch({ direction: v })} />
            <NumberField id={`${idPrefix}-value`} label="Points" value={num('value')} disabled={disabled} onChange={(v) => patch({ value: v })} />
          </div>
        </div>
      ) : kind === 'contains' ? (
        <div className="flex flex-wrap items-end gap-2">
          <SelectField id={`${idPrefix}-question`} label="Question" value={String(value.question ?? '')} options={targets.questions} words={Object.fromEntries(targets.questions.map((q) => [q, q]))} disabled={disabled} onChange={(v) => patch({ question: v })} />
          <SelectField id={`${idPrefix}-signal`} label="Signal" value={String(value.signal ?? '')} options={targets.signals} words={Object.fromEntries(targets.signals.map((s) => [s, s]))} disabled={disabled} onChange={(v) => patch({ signal: v })} />
        </div>
      ) : (
        <div className="flex flex-wrap items-end gap-3">
          <Operand id={`${idPrefix}-operand`} label="Compare" value={value} targets={targets} disabled={disabled} onChange={(next) => onChange({ operator: op, ...next, ...(kind === 'compare' ? { value: value.value } : kind === 'between' ? { low: value.low, high: value.high } : kind === 'list' ? { values: value.values } : {}) })} />
          {kind === 'compare' &&
            (typeof value.value === 'boolean' ? (
              <SelectField id={`${idPrefix}-bool`} label="Value" value={String(value.value)} options={['true', 'false']} disabled={disabled} onChange={(v) => patch({ value: v === 'true' })} />
            ) : (
              <NumberField id={`${idPrefix}-value`} label="Value" value={num('value')} disabled={disabled} onChange={(v) => patch({ value: v })} />
            ))}
          {kind === 'between' && (
            <>
              <NumberField id={`${idPrefix}-low`} label="From" value={num('low')} disabled={disabled} onChange={(v) => patch({ low: v })} />
              <NumberField id={`${idPrefix}-high`} label="To" value={num('high')} disabled={disabled} onChange={(v) => patch({ high: v })} />
            </>
          )}
          {kind === 'list' && <NumberListField id={`${idPrefix}-values`} label="Values" value={Array.isArray(value.values) ? (value.values as number[]) : []} disabled={disabled} onChange={(v) => patch({ values: v })} />}
        </div>
      )}
    </div>
  );
}
