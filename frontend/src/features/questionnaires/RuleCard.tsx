import { Button } from '@/components/ui/Button';
import { Switch, inputClass, selectClass, textareaClass } from '@/components/ui/Form';
import type { PenaltySpec, QuestionSpec, RuleSpec } from '@/types/api';
import { humanizeCode } from '@/utils/presentation';
import { ConditionEditor } from './ConditionEditor';
import { DIMENSIONS, SEVERITIES, type Cond } from './model';

interface Props {
  rule: RuleSpec;
  index: number;
  questions: QuestionSpec[];
  readOnly: boolean;
  onChange: (next: RuleSpec) => void;
  onRemove: () => void;
}

const STACKING: { id: string; label: string }[] = [
  { id: 'STACK', label: 'Add to other deductions' },
  { id: 'MAX', label: 'Only the largest applies' },
  { id: 'MAX_PER_CATEGORY', label: 'Largest per category' },
  { id: 'PRIORITY', label: 'Highest priority applies' },
  { id: 'CAPPED_STACK', label: 'Add up to a cap' },
];

const num = (value: string): number | undefined => (value.trim() === '' || Number.isNaN(Number(value)) ? undefined : Number(value));

/** One authored rule (decision D50): a condition that detects a pattern, and optionally the deduction and the advice it causes. */
export function RuleCard({ rule, index, questions, readOnly, onChange, onRemove }: Props) {
  const id = `rule-${index}`;
  const label = `Rule ${index + 1}`;
  const set = (patch: Partial<RuleSpec>) => onChange({ ...rule, ...patch });
  const penalty = rule.penalty;
  const setPenalty = (patch: Partial<PenaltySpec>) => set({ penalty: { ...(penalty as PenaltySpec), ...patch } });

  return (
    <li className="space-y-3 rounded-xl border border-line bg-canvas/40 p-4" aria-label={label}>
      <div className="flex flex-wrap items-center gap-2">
        <label className="sr-only" htmlFor={`${id}-name`}>
          {label} name
        </label>
        <input id={`${id}-name`} type="text" className={`${inputClass} !mt-0 flex-1`} placeholder="Name, e.g. Overloaded team without support" value={rule.name ?? ''} readOnly={readOnly} onChange={(e) => set({ name: e.target.value })} />
        <label className="sr-only" htmlFor={`${id}-severity`}>
          {label} severity
        </label>
        <select id={`${id}-severity`} className={`${selectClass} !mt-0 !w-auto`} value={rule.severity ?? 'MEDIUM'} disabled={readOnly} onChange={(e) => set({ severity: e.target.value })}>
          {SEVERITIES.map((s) => (
            <option key={s} value={s}>
              {humanizeCode(s)} severity
            </option>
          ))}
        </select>
        <Switch label="Active" checked={rule.enabled !== false} disabled={readOnly} onChange={(enabled) => set({ enabled })} />
        {!readOnly && (
          <Button variant="ghost" aria-label={`Remove ${label}`} onClick={onRemove}>
            Remove
          </Button>
        )}
      </div>
      <label className="sr-only" htmlFor={`${id}-description`}>
        {label} description
      </label>
      <input id={`${id}-description`} type="text" className={inputClass} placeholder="What the pattern is, in one sentence" value={rule.description ?? ''} readOnly={readOnly} onChange={(e) => set({ description: e.target.value })} />
      <label className="sr-only" htmlFor={`${id}-explanation`}>
        {label} explanation
      </label>
      <textarea id={`${id}-explanation`} rows={2} className={textareaClass} placeholder="Why it matters, shown when the pattern is detected (optional)" value={rule.explanation ?? ''} readOnly={readOnly} onChange={(e) => set({ explanation: e.target.value })} />

      <fieldset className="min-w-0 space-y-2">
        <legend className="text-sm font-medium">The pattern is detected when</legend>
        <p className="text-xs text-ink-soft">A question is compared by its answer on the 0 to 100 scale (100 is the desirable end); a dimension by its raw score.</p>
        <ConditionEditor
          value={(rule.condition as Cond | undefined) ?? { op: 'LTE', value: 25 }}
          onChange={(condition) => set({ condition: condition as RuleSpec['condition'] })}
          questions={questions}
          idPrefix={`${id}-cond`}
          readOnly={readOnly}
        />
      </fieldset>

      <div className="flex flex-wrap items-center gap-3 text-sm">
        <label htmlFor={`${id}-signals`}>Independent signals needed</label>
        <input id={`${id}-signals`} type="number" min={1} className={`${inputClass} !mt-0 !w-20`} value={rule.minimumSignals ?? 1} readOnly={readOnly} onChange={(e) => set({ minimumSignals: num(e.target.value) })} />
        <label htmlFor={`${id}-confidence`}>Minimum confidence</label>
        <input id={`${id}-confidence`} type="number" min={0} max={1} step={0.05} className={`${inputClass} !mt-0 !w-24`} value={rule.minimumConfidence ?? 0.5} readOnly={readOnly} onChange={(e) => set({ minimumConfidence: num(e.target.value) })} />
      </div>

      <div className="space-y-2 border-t border-line pt-3">
        <label className="inline-flex items-center gap-2 text-sm font-medium">
          <input
            type="checkbox"
            checked={!!penalty}
            disabled={readOnly}
            onChange={(e) => set({ penalty: e.target.checked ? { target: 'TEAM_HEALTH', strategy: 'FIXED', amount: 5, stackingPolicy: 'STACK' } : undefined })}
          />
          Detecting it lowers the score
        </label>
        {penalty && (
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <label htmlFor={`${id}-target`}>Lowers</label>
            <select id={`${id}-target`} className={`${selectClass} !mt-0 !w-auto`} value={penalty.target ?? 'TEAM_HEALTH'} disabled={readOnly} onChange={(e) => setPenalty({ target: e.target.value })}>
              {DIMENSIONS.map((d) => (
                <option key={d} value={d}>
                  {humanizeCode(d)}
                </option>
              ))}
              <option value="OVERALL">The overall score</option>
            </select>
            <label htmlFor={`${id}-strategy`}>by</label>
            <select id={`${id}-strategy`} className={`${selectClass} !mt-0 !w-auto`} value={penalty.strategy ?? 'FIXED'} disabled={readOnly} onChange={(e) => setPenalty({ strategy: e.target.value })}>
              <option value="FIXED">a fixed amount</option>
              <option value="SEVERITY_BASED">up to an amount, scaled by severity</option>
            </select>
            <label className="sr-only" htmlFor={`${id}-amount`}>
              Points
            </label>
            <input id={`${id}-amount`} type="number" min={0} max={100} className={`${inputClass} !mt-0 !w-20`} value={penalty.amount ?? ''} readOnly={readOnly} onChange={(e) => setPenalty({ amount: num(e.target.value) })} />
            <span className="text-ink-soft">points</span>
            <label htmlFor={`${id}-stacking`}>Several deductions</label>
            <select id={`${id}-stacking`} className={`${selectClass} !mt-0 !w-auto`} value={penalty.stackingPolicy ?? 'STACK'} disabled={readOnly} onChange={(e) => setPenalty({ stackingPolicy: e.target.value })}>
              {STACKING.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.label}
                </option>
              ))}
            </select>
            {penalty.stackingPolicy === 'CAPPED_STACK' && (
              <>
                <label htmlFor={`${id}-cap`}>Cap</label>
                <input id={`${id}-cap`} type="number" min={0} className={`${inputClass} !mt-0 !w-20`} value={penalty.cumulativeCap ?? ''} readOnly={readOnly} onChange={(e) => setPenalty({ cumulativeCap: num(e.target.value) })} />
              </>
            )}
          </div>
        )}
      </div>

      <div className="space-y-2 border-t border-line pt-3">
        <label className="inline-flex items-center gap-2 text-sm font-medium">
          <input
            type="checkbox"
            checked={!!rule.recommendation}
            disabled={readOnly}
            onChange={(e) => set({ recommendation: e.target.checked ? { title: '', description: '' } : undefined })}
          />
          Recommend an action
        </label>
        {rule.recommendation && (
          <div className="grid gap-2">
            <label className="sr-only" htmlFor={`${id}-rec-title`}>
              Recommendation title
            </label>
            <input id={`${id}-rec-title`} type="text" className={`${inputClass} !mt-0`} placeholder="Action, e.g. Review the team's workload" value={rule.recommendation.title ?? ''} readOnly={readOnly} onChange={(e) => set({ recommendation: { ...rule.recommendation, title: e.target.value } })} />
            <label className="sr-only" htmlFor={`${id}-rec-description`}>
              Recommendation description
            </label>
            <textarea id={`${id}-rec-description`} rows={2} className={textareaClass} placeholder="What to do and why" value={rule.recommendation.description ?? ''} readOnly={readOnly} onChange={(e) => set({ recommendation: { ...rule.recommendation, description: e.target.value } })} />
          </div>
        )}
      </div>
    </li>
  );
}
