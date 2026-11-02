import { useState } from 'react';
import { Field, inputClass } from '@/components/ui/Form';

/**
 * A number the person types: it keeps what was typed (so "0." and an empty box are possible while editing) and reports a value only
 * when the text is a number. The server remains the judge of what is allowed; the hints here only guide.
 */
export function NumberField({ id, label, value, onChange, step = 'any', help, error, disabled, className }: { id: string; label: string; value: number | undefined; onChange: (value: number) => void; step?: string; help?: string; error?: string; disabled?: boolean; className?: string }) {
  const [raw, setRaw] = useState<string | null>(null);
  const shown = raw !== null && (raw === '' || Number(raw) === value) ? raw : value === undefined ? '' : String(value);
  return (
    <Field id={id} label={label} help={help} error={error} className={className}>
      <input
        id={id}
        type="number"
        inputMode="decimal"
        step={step}
        disabled={disabled}
        className={inputClass}
        value={shown}
        aria-invalid={!!error}
        aria-describedby={`${help ? `${id}-help ` : ''}${id}-error`}
        onChange={(e) => {
          setRaw(e.target.value);
          if (e.target.value.trim() !== '' && Number.isFinite(Number(e.target.value))) onChange(Number(e.target.value));
        }}
      />
    </Field>
  );
}

export function TextField({ id, label, value, onChange, multiline = false, disabled, className }: { id: string; label: string; value: string | undefined; onChange: (value: string) => void; multiline?: boolean; disabled?: boolean; className?: string }) {
  return (
    <Field id={id} label={label} className={className}>
      {multiline ? (
        <textarea id={id} rows={3} disabled={disabled} className={`${inputClass} py-2`} value={value ?? ''} onChange={(e) => onChange(e.target.value)} />
      ) : (
        <input id={id} type="text" disabled={disabled} className={inputClass} value={value ?? ''} onChange={(e) => onChange(e.target.value)} />
      )}
    </Field>
  );
}
