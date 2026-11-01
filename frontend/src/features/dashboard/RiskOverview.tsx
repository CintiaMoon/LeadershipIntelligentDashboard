import { Card } from '@/components/ui/Card';
import { SeverityBadge } from '@/components/patterns/SeverityBadge';
import { SEVERITIES } from '@/utils/labels';

interface Props {
  counts: Record<string, number>;
  active: string | null;
  onToggle: (severity: string) => void;
}

/** Detected patterns by severity. Each count filters the pattern list below it. */
export function RiskOverview({ counts, active, onToggle }: Props) {
  return (
    <section aria-labelledby="risk-heading">
      <h2 id="risk-heading" className="mb-3 text-lg font-semibold">
        Risk overview
      </h2>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {SEVERITIES.map((severity) => {
          const count = counts[severity] ?? 0;
          const pressed = active === severity;
          return (
            <button
              key={severity}
              type="button"
              aria-pressed={pressed}
              disabled={count === 0}
              onClick={() => onToggle(severity)}
              className="text-left disabled:cursor-default"
            >
              <Card className={`p-4 transition-colors ${pressed ? 'ring-2 ring-accent' : count > 0 ? 'hover:border-accent' : 'opacity-70'}`}>
                <SeverityBadge severity={severity} />
                <p className="mt-3 text-3xl font-semibold tabular-nums">{count}</p>
                <p className="text-xs text-ink-soft">{count === 1 ? 'pattern detected' : 'patterns detected'}</p>
              </Card>
            </button>
          );
        })}
      </div>
    </section>
  );
}
