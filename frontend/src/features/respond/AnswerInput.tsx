import { inputClass, textareaClass } from '@/components/ui/Form';
import type { RespondentQuestion } from '@/types/api';

interface Props {
  question: RespondentQuestion;
  value: unknown;
  readOnly: boolean;
  invalid: boolean;
  onChange: (value: unknown) => void;
}

const choiceClass = 'inline-flex min-h-9 min-w-9 cursor-pointer items-center justify-center rounded-md border border-line bg-surface px-3 text-sm has-[:checked]:border-accent has-[:checked]:bg-accent-soft has-[:checked]:text-accent-ink has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-accent';

/**
 * The control for one question, chosen by its kind (decision D48). A respondent sees the question and its options and nothing about
 * scoring. Every control is a native input inside a labelled group, so it works with the keyboard and with a screen reader.
 */
export function AnswerInput({ question: q, value, readOnly, invalid, onChange }: Props) {
  const id = `a-${q.id}`;
  const legend = (
    <legend className="text-sm font-medium">
      {q.text}
      {q.required && <span className="text-ink-soft"> (required)</span>}
    </legend>
  );
  const help = q.help ? (
    <p id={`${id}-help`} className="mt-0.5 text-xs text-ink-soft">
      {q.help}
    </p>
  ) : null;
  const problem = invalid ? (
    <p role="alert" className="mt-1 text-xs text-risk-critical">
      This question needs an answer.
    </p>
  ) : null;
  const group = (children: React.ReactNode) => (
    <fieldset className="min-w-0 space-y-2" aria-describedby={q.help ? `${id}-help` : undefined} aria-invalid={invalid}>
      {legend}
      {help}
      {children}
      {problem}
    </fieldset>
  );

  switch (q.kind) {
    case 'RATING':
    case 'LIKERT':
    case 'NPS': {
      const min = q.min ?? 1;
      const max = q.max ?? 5;
      const scale = Array.from({ length: max - min + 1 }, (_, i) => min + i);
      return group(
        <div className="flex flex-wrap items-center gap-2">
          {scale.map((n) => (
            <label key={n} className={choiceClass}>
              <input type="radio" className="sr-only" name={id} value={n} checked={value === n} disabled={readOnly} onChange={() => onChange(n)} />
              <span>{n}</span>
            </label>
          ))}
          <span className="text-xs text-ink-soft">
            {min} lowest, {max} highest
          </span>
        </div>,
      );
    }
    case 'YES_NO':
      return group(
        <div className="flex gap-2">
          {[
            ['Yes', true],
            ['No', false],
          ].map(([label, v]) => (
            <label key={String(label)} className={choiceClass}>
              <input type="radio" className="sr-only" name={id} checked={value === v} disabled={readOnly} onChange={() => onChange(v)} />
              <span>{String(label)}</span>
            </label>
          ))}
        </div>,
      );
    case 'SINGLE_CHOICE':
      return group(
        <div className="flex flex-col gap-2">
          {(q.options ?? []).map((o) => (
            <label key={o.key} className="flex cursor-pointer items-center gap-2 text-sm">
              <input type="radio" name={id} checked={value === o.key} disabled={readOnly} onChange={() => onChange(o.key)} />
              <span>{o.label}</span>
            </label>
          ))}
        </div>,
      );
    case 'MULTI_CHOICE': {
      const chosen = Array.isArray(value) ? (value as string[]) : [];
      return group(
        <div className="flex flex-col gap-2">
          {(q.options ?? []).map((o) => (
            <label key={o.key} className="flex cursor-pointer items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={chosen.includes(o.key as string)}
                disabled={readOnly}
                onChange={(e) => onChange(e.target.checked ? [...chosen, o.key] : chosen.filter((k) => k !== o.key))}
              />
              <span>{o.label}</span>
            </label>
          ))}
        </div>,
      );
    }
    case 'NUMERIC':
      return group(
        <div className="flex items-center gap-2">
          <label className="sr-only" htmlFor={id}>
            {q.text}
          </label>
          <input
            id={id}
            type="number"
            className={`${inputClass} !mt-0 !w-32`}
            min={q.min}
            max={q.max}
            value={typeof value === 'number' ? value : ''}
            readOnly={readOnly}
            onChange={(e) => onChange(e.target.value === '' ? undefined : Number(e.target.value))}
          />
          {q.min !== undefined && q.max !== undefined && (
            <span className="text-xs text-ink-soft">
              between {q.min} and {q.max}
            </span>
          )}
        </div>,
      );
    default:
      return group(
        <>
          <label className="sr-only" htmlFor={id}>
            {q.text}
          </label>
          <textarea id={id} rows={4} maxLength={4000} className={textareaClass} value={typeof value === 'string' ? value : ''} readOnly={readOnly} onChange={(e) => onChange(e.target.value)} />
        </>,
      );
  }
}
