import { useState } from 'react';
import { Field, inputClass, selectClass } from '@/components/ui/Form';
import { humanizeCode } from '@/utils/presentation';

export function SelectField({ id, label, value, options, onChange, disabled, className, words }: { id: string; label: string; value: string; options: string[]; onChange: (value: string) => void; disabled?: boolean; className?: string; words?: Record<string, string> }) {
  // a value the list does not know (typed in JSON) stays selectable so it is not silently replaced
  const all = options.includes(value) || value === '' ? options : [...options, value];
  return (
    <Field id={id} label={label} className={className}>
      <select id={id} disabled={disabled} className={selectClass} value={value} onChange={(e) => onChange(e.target.value)}>
        {value === '' && <option value="">Choose</option>}
        {all.map((o) => (
          <option key={o} value={o}>
            {words?.[o] ?? humanizeCode(o)}
          </option>
        ))}
      </select>
    </Field>
  );
}

/** A choice of several from a list, as checkboxes (which questions feed a signal, which advice a pattern gives). */
export function CheckGroup({ legend, options, selected, onChange, disabled }: { legend: string; options: string[]; selected: string[]; onChange: (next: string[]) => void; disabled?: boolean }) {
  const all = [...options, ...selected.filter((s) => !options.includes(s))];
  return (
    <fieldset className="min-w-0">
      <legend className="text-sm font-medium">{legend}</legend>
      <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1">
        {all.map((o) => (
          <label key={o} className="flex items-center gap-1.5 text-sm">
            <input
              type="checkbox"
              disabled={disabled}
              checked={selected.includes(o)}
              onChange={(e) => onChange(e.target.checked ? [...selected, o] : selected.filter((x) => x !== o))}
            />
            <span className="font-mono text-xs">{o}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

/** A list of numbers typed with commas; it reports a new list only while every part is a number. */
export function NumberListField({ id, label, value, onChange, disabled }: { id: string; label: string; value: number[]; onChange: (next: number[]) => void; disabled?: boolean }) {
  const [raw, setRaw] = useState<string | null>(null);
  const shown = raw !== null && sameNumbers(parse(raw), value) ? raw : value.join(', ');
  return (
    <Field id={id} label={label} help="Separate with commas.">
      <input
        id={id}
        type="text"
        inputMode="decimal"
        disabled={disabled}
        className={inputClass}
        value={shown}
        aria-describedby={`${id}-help`}
        onChange={(e) => {
          setRaw(e.target.value);
          const parsed = parse(e.target.value);
          if (parsed) onChange(parsed);
        }}
      />
    </Field>
  );
}

function parse(text: string): number[] | null {
  const parts = text.split(',').map((p) => p.trim()).filter((p) => p !== '');
  const numbers = parts.map(Number);
  return numbers.every(Number.isFinite) ? numbers : null;
}

const sameNumbers = (a: number[] | null, b: number[]) => a !== null && a.length === b.length && a.every((x, i) => x === b[i]);
