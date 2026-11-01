import { Button } from '@/components/ui/Button';
import { Switch, inputClass, selectClass, textareaClass } from '@/components/ui/Form';
import type { QuestionSpec } from '@/types/api';
import { humanizeCode } from '@/utils/presentation';
import { DIMENSIONS, KINDS, changeKind, kindLabel, newOptionKey, type QuestionKind } from './model';

interface Props {
  question: QuestionSpec;
  index: number;
  count: number;
  readOnly: boolean;
  onChange: (next: QuestionSpec) => void;
  onMove: (direction: -1 | 1) => void;
  onRemove: () => void;
}

const num = (value: string): number | undefined => (value.trim() === '' || Number.isNaN(Number(value)) ? undefined : Number(value));

/** One question of the builder (decision D47): its text, kind, dimension, weight and the settings that kind needs. */
export function QuestionCard({ question: q, index, count, readOnly, onChange, onMove, onRemove }: Props) {
  const id = `q-${q.id}`;
  const kind = q.kind as QuestionKind;
  const scorable = KINDS.find((k) => k.kind === kind)?.scorable ?? false;
  const set = (patch: Partial<QuestionSpec>) => onChange({ ...q, ...patch });
  const options = q.options ?? [];
  const label = `Question ${index + 1}`;

  return (
    <li className="rounded-xl border border-line bg-canvas/40 p-4" aria-label={`${label}, ${kindLabel(kind)}`}>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        <span className="font-mono text-xs text-ink-faint" aria-hidden="true">
          {String(index + 1).padStart(2, '0')}
        </span>
        <label className="sr-only" htmlFor={`${id}-kind`}>
          {label} type
        </label>
        <select id={`${id}-kind`} className={`${selectClass} !mt-0 !min-h-9 !w-auto`} value={kind} disabled={readOnly} onChange={(e) => onChange(changeKind(q, e.target.value as QuestionKind))}>
          {KINDS.map((k) => (
            <option key={k.kind} value={k.kind}>
              {k.label}
            </option>
          ))}
        </select>
        {scorable ? (
          <>
            <label className="sr-only" htmlFor={`${id}-dimension`}>
              {label} dimension
            </label>
            <select
              id={`${id}-dimension`}
              className={`${selectClass} !mt-0 !min-h-9 !w-auto`}
              value={q.dimension ?? ''}
              disabled={readOnly}
              onChange={(e) => set({ dimension: e.target.value || undefined })}
            >
              <option value="">Collected, not scored</option>
              {DIMENSIONS.map((d) => (
                <option key={d} value={d}>
                  {humanizeCode(d)}
                </option>
              ))}
            </select>
          </>
        ) : (
          <span className="text-xs text-ink-soft">Collected, never scored</span>
        )}
        <div className="ml-auto flex items-center gap-2">
          <Switch label="Required" checked={!!q.required} disabled={readOnly} onChange={(required) => set({ required })} />
          {!readOnly && (
            <>
              <Button variant="ghost" aria-label={`Move ${label} up`} disabled={index === 0} onClick={() => onMove(-1)}>
                &uarr;
              </Button>
              <Button variant="ghost" aria-label={`Move ${label} down`} disabled={index === count - 1} onClick={() => onMove(1)}>
                &darr;
              </Button>
              <Button variant="ghost" aria-label={`Remove ${label}`} onClick={onRemove}>
                Remove
              </Button>
            </>
          )}
        </div>
      </div>

      <label className="sr-only" htmlFor={`${id}-text`}>
        {label} text
      </label>
      <textarea
        id={`${id}-text`}
        rows={2}
        className={textareaClass}
        placeholder="Write the question..."
        value={q.text ?? ''}
        readOnly={readOnly}
        onChange={(e) => set({ text: e.target.value })}
      />
      <label className="sr-only" htmlFor={`${id}-help`}>
        {label} help text
      </label>
      <input id={`${id}-help`} type="text" className={inputClass} placeholder="Help text (optional)" value={q.help ?? ''} readOnly={readOnly} onChange={(e) => set({ help: e.target.value })} />

      {(kind === 'SINGLE_CHOICE' || kind === 'MULTI_CHOICE') && (
        <div className="mt-3 space-y-2">
          <ul className="space-y-2" aria-label={`${label} options`}>
            {options.map((o, i) => (
              <li key={o.key} className="flex items-center gap-2">
                <label className="sr-only" htmlFor={`${id}-o${i}`}>
                  Option {i + 1} label
                </label>
                <input
                  id={`${id}-o${i}`}
                  type="text"
                  className={`${inputClass} !mt-0`}
                  value={o.label ?? ''}
                  readOnly={readOnly}
                  onChange={(e) => set({ options: options.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)) })}
                />
                <label className="sr-only" htmlFor={`${id}-s${i}`}>
                  Option {i + 1} score
                </label>
                <input
                  id={`${id}-s${i}`}
                  type="number"
                  min={0}
                  max={100}
                  className={`${inputClass} !mt-0 !w-24`}
                  placeholder="Auto"
                  title="Score from 0 to 100 (100 is desirable). Leave every option empty to space them evenly in the order shown."
                  value={o.score ?? ''}
                  readOnly={readOnly}
                  onChange={(e) => set({ options: options.map((x, j) => (j === i ? { ...x, score: num(e.target.value) } : x)) })}
                />
                {!readOnly && (
                  <Button variant="ghost" aria-label={`Remove option ${i + 1} of ${label}`} disabled={options.length <= 2} onClick={() => set({ options: options.filter((_, j) => j !== i) })}>
                    Remove
                  </Button>
                )}
              </li>
            ))}
          </ul>
          <p className="text-xs text-ink-soft">
            Scores run from 0 to 100, where 100 is the desirable end. Leave all scores empty to space the options evenly, first option 0 and last option 100.
          </p>
          {!readOnly && (
            <Button onClick={() => set({ options: [...options, { key: newOptionKey(options), label: `Option ${options.length + 1}` }] })}>Add option</Button>
          )}
        </div>
      )}

      {kind === 'LIKERT' && (
        <div className="mt-3 flex items-center gap-2 text-sm">
          <label htmlFor={`${id}-points`}>Points</label>
          <select id={`${id}-points`} className={`${selectClass} !mt-0 !w-auto`} value={q.points ?? 5} disabled={readOnly} onChange={(e) => set({ points: Number(e.target.value) })}>
            <option value={5}>5</option>
            <option value={7}>7</option>
          </select>
        </div>
      )}

      {kind === 'NUMERIC' && (
        <div className="mt-3 flex flex-wrap items-center gap-3 text-sm">
          <label htmlFor={`${id}-min`}>Minimum</label>
          <input id={`${id}-min`} type="number" className={`${inputClass} !mt-0 !w-28`} value={q.min ?? ''} readOnly={readOnly} onChange={(e) => set({ min: num(e.target.value) })} />
          <label htmlFor={`${id}-max`}>Maximum</label>
          <input id={`${id}-max`} type="number" className={`${inputClass} !mt-0 !w-28`} value={q.max ?? ''} readOnly={readOnly} onChange={(e) => set({ max: num(e.target.value) })} />
          <span className="text-xs text-ink-soft">An answer outside the range counts as the nearest end of it.</span>
        </div>
      )}

      {kind === 'YES_NO' && (
        <div className="mt-3 flex items-center gap-2 text-sm">
          <label htmlFor={`${id}-desirable`}>The desirable answer is</label>
          <select id={`${id}-desirable`} className={`${selectClass} !mt-0 !w-auto`} value={q.desirableAnswer ?? 'YES'} disabled={readOnly} onChange={(e) => set({ desirableAnswer: e.target.value })}>
            <option value="YES">Yes</option>
            <option value="NO">No</option>
          </select>
        </div>
      )}

      {(kind === 'RATING' || kind === 'LIKERT' || kind === 'NPS' || kind === 'NUMERIC') && (
        <div className="mt-3 flex items-center gap-2 text-sm">
          <label htmlFor={`${id}-direction`}>A higher answer is</label>
          <select
            id={`${id}-direction`}
            className={`${selectClass} !mt-0 !w-auto`}
            value={q.higherIsBetter === false ? 'worse' : 'better'}
            disabled={readOnly}
            onChange={(e) => set({ higherIsBetter: e.target.value === 'better' })}
          >
            <option value="better">better</option>
            <option value="worse">worse</option>
          </select>
        </div>
      )}

      {scorable && q.dimension && (
        <div className="mt-3 flex items-center gap-2 text-sm">
          <label htmlFor={`${id}-weight`}>Weight in {humanizeCode(q.dimension)}</label>
          <input
            id={`${id}-weight`}
            type="number"
            min={0.1}
            step={0.1}
            className={`${inputClass} !mt-0 !w-24`}
            value={q.weight ?? 1}
            readOnly={readOnly}
            onChange={(e) => set({ weight: num(e.target.value) })}
          />
        </div>
      )}
    </li>
  );
}
