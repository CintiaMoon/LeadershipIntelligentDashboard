import { useState, type ReactNode } from 'react';
import { brand } from '@/brand';
import { BrandMark } from '@/components/ui/BrandMark';
import { Card } from '@/components/ui/Card';
import { Field, inputClass } from '@/components/ui/Form';

/** The frame of every page that is shown before there is a company: the mark, a title, and a card. */
export function AuthShell({ title, subtitle, children, footer }: { title: string; subtitle?: ReactNode; children: ReactNode; footer?: ReactNode }) {
  return (
    <main className="mx-auto grid min-h-screen max-w-md place-items-center px-4 py-12">
      <div className="w-full space-y-6">
        <div className="text-center">
          <BrandMark className="mx-auto size-12" />
          <p className="mt-3 font-display text-lg font-semibold tracking-tight">{brand.productName}</p>
        </div>
        <Card className="p-6">
          <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-ink-soft">{subtitle}</p>}
          <div className="mt-5 space-y-4">{children}</div>
        </Card>
        {footer && <p className="text-center text-sm text-ink-soft">{footer}</p>}
      </div>
    </main>
  );
}

/** What the password rule is, in words (decision D65): the server decides, this only tells people what to expect. */
export const PASSWORD_HELP = 'At least 12 characters. A phrase works well; there are no other rules, but common and easy-to-guess passwords are refused.';

/** A password field with a switch to see what was typed. */
export function PasswordField({
  id,
  label,
  value,
  onChange,
  autoComplete,
  help,
  error,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete: 'current-password' | 'new-password';
  help?: ReactNode;
  error?: string;
}) {
  const [shown, setShown] = useState(false);
  return (
    <Field id={id} label={label} help={help} error={error}>
      <div className="relative">
        <input
          id={id}
          type={shown ? 'text' : 'password'}
          autoComplete={autoComplete}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          aria-invalid={!!error}
          aria-describedby={`${id}-help ${id}-error`}
          maxLength={1000}
          className={`${inputClass} pr-16`}
        />
        <button
          type="button"
          onClick={() => setShown((s) => !s)}
          aria-pressed={shown}
          className="absolute right-2 top-1/2 -translate-y-1/2 rounded px-2 py-1 text-xs text-ink-soft hover:text-ink"
        >
          {shown ? 'Hide' : 'Show'}
        </button>
      </div>
    </Field>
  );
}

export function TextField({
  id,
  label,
  value,
  onChange,
  type = 'text',
  autoComplete,
  help,
  error,
  required,
  maxLength,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: 'text' | 'email';
  autoComplete?: string;
  help?: ReactNode;
  error?: string;
  required?: boolean;
  maxLength?: number;
}) {
  return (
    <Field id={id} label={label} help={help} error={error}>
      <input
        id={id}
        type={type}
        autoComplete={autoComplete}
        value={value}
        required={required}
        maxLength={maxLength}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={!!error}
        aria-describedby={`${id}-help ${id}-error`}
        className={inputClass}
      />
    </Field>
  );
}

/** A message shown after a form was sent or failed. */
export function Notice({ tone, children }: { tone: 'ok' | 'error'; children: ReactNode }) {
  const classes = tone === 'ok' ? 'border-ok/30 bg-ok-soft text-ok' : 'border-risk-critical/30 bg-risk-critical-soft text-risk-critical';
  return (
    <div role={tone === 'ok' ? 'status' : 'alert'} className={`rounded-lg border px-4 py-3 text-sm ${classes}`}>
      {children}
    </div>
  );
}
