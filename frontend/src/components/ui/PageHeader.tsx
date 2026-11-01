import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

export function BackLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="inline-flex items-center gap-1 text-sm text-ink-soft hover:text-ink">
      <span aria-hidden="true">&larr;</span> {children}
    </Link>
  );
}

export function PageHeader({ title, subtitle, back, actions }: { title: string; subtitle?: ReactNode; back?: ReactNode; actions?: ReactNode }) {
  return (
    <header className="space-y-3">
      {back}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-3xl font-semibold tracking-tight">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-ink-soft">{subtitle}</p>}
        </div>
        {actions}
      </div>
    </header>
  );
}
