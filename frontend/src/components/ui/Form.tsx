import type { ReactNode } from 'react';
import { describeError } from '@/services/api/errors';

/** One input style for every form, so fields look and behave the same everywhere. */
export const inputClass =
  'mt-1 min-h-10 w-full rounded-md border border-line bg-surface px-3 text-sm text-ink placeholder:text-ink-faint aria-[invalid=true]:border-risk-critical disabled:opacity-60';

export const selectClass = inputClass;

export const textareaClass = `${inputClass} py-2`;

/** A label, a control, optional help and a live error, wired together for assistive technology. */
export function Field({
  id,
  label,
  help,
  error,
  children,
  className = '',
}: {
  id: string;
  label: ReactNode;
  help?: ReactNode;
  error?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={className}>
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      {children}
      {help && (
        <p id={`${id}-help`} className="mt-1 text-xs text-ink-soft">
          {help}
        </p>
      )}
      <p id={`${id}-error`} role="alert" className="mt-1 text-xs text-risk-critical">
        {error}
      </p>
    </div>
  );
}

/** A request failure shown next to the form that caused it, with the reference support needs. */
export function FormError({ error, details }: { error: unknown; details?: string[] }) {
  if (!error) return null;
  const view = describeError(error);
  return (
    <div role="alert" className="rounded-lg border border-risk-critical/30 bg-risk-critical-soft px-4 py-3 text-sm text-risk-critical">
      <p>
        <strong>{view.title}.</strong> {view.message}
      </p>
      {details && details.length > 0 && (
        <ul className="mt-2 list-disc space-y-0.5 pl-5">
          {details.map((d) => (
            <li key={d}>{d}</li>
          ))}
        </ul>
      )}
      {view.correlationId && <p className="mt-1 text-xs">Reference {view.correlationId}</p>}
    </div>
  );
}

/** An on/off setting that reads as a switch and is a checkbox underneath, so it works with every assistive technology. */
export function Switch({ label, checked, onChange, disabled }: { label: string; checked: boolean; onChange: (checked: boolean) => void; disabled?: boolean }) {
  return (
    <label className="inline-flex cursor-pointer items-center gap-2 text-xs text-ink-soft">
      <span>{label}</span>
      <input type="checkbox" role="switch" className="peer sr-only" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />
      <span
        aria-hidden="true"
        className="relative h-5 w-9 rounded-full border border-line bg-canvas transition-colors after:absolute after:left-0.5 after:top-0.5 after:size-3.5 after:rounded-full after:bg-ink-soft after:transition-transform peer-checked:border-accent peer-checked:bg-accent peer-checked:after:translate-x-4 peer-checked:after:bg-on-accent peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-accent"
      />
    </label>
  );
}

/** The few states a status can have, written out and coloured (colour is never the only signal). */
export function statusTone(state: string): 'ok' | 'accent' | 'medium' | 'neutral' | 'low' {
  switch (state) {
    case 'PUBLISHED':
    case 'SUBMITTED':
    case 'CLOSED':
      return 'ok';
    case 'PUBLISHED_WITH_DRAFT':
    case 'IN_PROGRESS':
    case 'OPEN':
      return 'accent';
    case 'DRAFT':
    case 'NOT_STARTED':
      return 'medium';
    default:
      return 'neutral';
  }
}
