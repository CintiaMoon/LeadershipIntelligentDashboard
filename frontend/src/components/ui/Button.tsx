import type { ButtonHTMLAttributes } from 'react';

type Variant = 'primary' | 'secondary' | 'ghost';

const styles: Record<Variant, string> = {
  primary: 'bg-accent text-white hover:bg-accent-ink disabled:opacity-50',
  secondary: 'border border-line bg-surface text-ink hover:bg-canvas disabled:opacity-50',
  ghost: 'text-ink-soft hover:bg-accent-soft hover:text-accent-ink disabled:opacity-50',
};

export function Button({ variant = 'secondary', className = '', type = 'button', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      type={type}
      className={`inline-flex min-h-9 items-center justify-center gap-2 rounded-md px-3.5 text-sm font-medium transition-colors ${styles[variant]} ${className}`}
      {...props}
    />
  );
}
