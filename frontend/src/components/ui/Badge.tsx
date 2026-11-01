import type { ReactNode } from 'react';

export type Tone = 'neutral' | 'accent' | 'ok' | 'low' | 'medium' | 'high' | 'critical';

const tones: Record<Tone, string> = {
  neutral: 'bg-canvas text-ink-soft ring-line',
  accent: 'bg-accent-soft text-accent-ink ring-accent/20',
  ok: 'bg-ok-soft text-ok ring-ok/20',
  low: 'bg-risk-low-soft text-risk-low ring-risk-low/20',
  medium: 'bg-risk-medium-soft text-risk-medium ring-risk-medium/30',
  high: 'bg-risk-high-soft text-risk-high ring-risk-high/30',
  critical: 'bg-risk-critical-soft text-risk-critical ring-risk-critical/30',
};

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${tones[tone]}`}>{children}</span>;
}
