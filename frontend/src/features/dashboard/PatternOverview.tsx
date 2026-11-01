import { PatternCard } from '@/components/patterns/PatternCard';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/StateViews';
import type { Pattern, Penalty } from '@/types/views';
import { humanizeCode } from '@/utils/presentation';
import type { Names } from './display';
import { affectedDimensions, detectedPatterns, groupPatterns, insufficientEvidencePatterns, type GroupBy } from './selectors';

interface Props {
  patterns: Pattern[];
  penalties: Penalty[];
  names: Names;
  actionTitles: Record<string, string>;
  redacted: boolean;
  runId?: string;
  groupBy: GroupBy;
  onGroupBy: (by: GroupBy) => void;
  severity: string | null;
  onClearSeverity: () => void;
}

const OPTIONS: { value: GroupBy; label: string }[] = [
  { value: 'dimension', label: 'Dimension' },
  { value: 'severity', label: 'Severity' },
  { value: 'confidence', label: 'Confidence' },
];

export function PatternOverview({ patterns, penalties, names, actionTitles, redacted, runId, groupBy, onGroupBy, severity, onClearSeverity }: Props) {
  const detected = detectedPatterns(patterns).filter((p) => !severity || p.severity === severity);
  const unproven = insufficientEvidencePatterns(patterns).filter((p) => !severity || p.severity === severity);
  const groups = groupPatterns(detected, groupBy);

  const heading = (key: string) => (groupBy === 'dimension' ? names.dimension(key).name : humanizeCode(key));
  const card = (p: Pattern) => (
    <PatternCard
      key={p.patternId}
      pattern={p}
      names={names}
      affected={affectedDimensions(p, penalties)}
      actions={p.actionKeys.map((key) => ({ key, title: actionTitles[key] ?? humanizeCode(key) }))}
      redacted={redacted}
      detailHref={runId ? `/runs/${runId}/patterns/${p.patternId}` : undefined}
    />
  );

  return (
    <section aria-labelledby="patterns-heading">
      <Card>
        <CardHeader
          title="Organizational patterns"
          description={`${detectedPatterns(patterns).length} detected of ${patterns.length} evaluated`}
          action={
            <fieldset className="flex flex-wrap items-center gap-1 text-sm">
              <legend className="sr-only">Group patterns by</legend>
              <span aria-hidden="true" className="mr-1 text-ink-soft">
                Group by
              </span>
              {OPTIONS.map((o) => (
                <label key={o.value} className={`cursor-pointer rounded-md px-2.5 py-1 ${groupBy === o.value ? 'bg-accent-soft font-medium text-accent-ink' : 'text-ink-soft hover:bg-canvas'}`}>
                  <input type="radio" name="group-patterns" className="sr-only" checked={groupBy === o.value} onChange={() => onGroupBy(o.value)} />
                  {o.label}
                </label>
              ))}
            </fieldset>
          }
        />
        <span id="patterns-heading" className="sr-only">
          Organizational patterns
        </span>
        <div className="space-y-6 p-5">
          {severity && (
            <p className="flex items-center gap-2 text-sm">
              <Badge tone="accent">Filter</Badge> Showing {humanizeCode(severity)} severity only.
              <Button variant="ghost" onClick={onClearSeverity}>
                Clear filter
              </Button>
            </p>
          )}
          {groups.length === 0 ? (
            <EmptyState
              title={severity ? 'No patterns at this severity' : 'No organizational patterns detected'}
              description="Patterns appear when the configured conditions are met with enough evidence."
            />
          ) : (
            groups.map((group) => (
              <div key={group.key} role="group" aria-label={heading(group.key)}>
                <h3 className="mb-2 text-sm font-semibold text-ink-soft">
                  {heading(group.key)} <span className="font-normal text-ink-faint">({group.patterns.length})</span>
                </h3>
                <div className="space-y-3">{group.patterns.map(card)}</div>
              </div>
            ))
          )}
          {unproven.length > 0 && (
            <details className="rounded-lg border border-line p-3">
              <summary className="cursor-pointer text-sm font-medium text-ink-soft">
                Conditions met but not enough evidence ({unproven.length})
              </summary>
              <p className="mt-2 text-xs text-ink-faint">These are not treated as findings and never lead to a penalty.</p>
              <div className="mt-3 space-y-3">{unproven.map(card)}</div>
            </details>
          )}
        </div>
      </Card>
    </section>
  );
}
