import { useState } from 'react';
import { Link } from 'react-router-dom';
import { EvidenceList } from '@/components/evidence/EvidenceList';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import type { Names } from '@/features/dashboard/display';
import type { Pattern } from '@/types/views';
import { patternReason } from '@/utils/labels';
import { ConfidenceIndicator } from './ConfidenceIndicator';
import { SeverityBadge } from './SeverityBadge';

interface Props {
  pattern: Pattern;
  names: Names;
  affected: string[];
  actions: { key: string; title: string }[];
  redacted: boolean;
  detailHref?: string;
}

export function PatternCard({ pattern, names, affected, actions, redacted, detailHref }: Props) {
  const [open, setOpen] = useState(false);
  const display = names.pattern(pattern.patternId, pattern.code);
  const detected = pattern.status === 'MATCHED';
  const evidenceId = `evidence-${pattern.patternId}`;

  return (
    <article id={`pattern-${pattern.patternId}`} className="rounded-lg border border-line bg-surface p-4" aria-labelledby={`title-${pattern.patternId}`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h4 id={`title-${pattern.patternId}`} className="text-sm font-semibold">
          {display.name}
        </h4>
        <div className="flex flex-wrap items-center gap-2">
          <SeverityBadge severity={pattern.severity} />
          {!detected && <Badge tone="neutral">Not enough evidence</Badge>}
        </div>
      </div>
      {display.description && <p className="mt-1 text-sm text-ink-soft">{display.description}</p>}
      {display.explanation && detected && <p className="mt-2 text-sm text-ink">{display.explanation}</p>}
      {!detected && <p className="mt-2 text-sm text-ink-soft">{patternReason(pattern.reason)}.</p>}

      <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-1">
        <ConfidenceIndicator band={pattern.confidenceBand} value={pattern.confidence} />
        <span className="text-xs text-ink-soft">
          {pattern.evidence.length} {pattern.evidence.length === 1 ? 'evidence item' : 'evidence items'}
        </span>
        {affected.length > 0 && <span className="text-xs text-ink-soft">Affects: {affected.map((d) => names.dimension(d).name).join(', ')}</span>}
      </div>

      {actions.length > 0 && (
        <div className="mt-3">
          <p className="text-xs font-medium text-ink-soft">Recommended actions</p>
          <ul className="mt-1 list-disc pl-5 text-sm">
            {actions.map((a) => (
              <li key={a.key}>{a.title}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="mt-3 flex flex-wrap items-center gap-2">
        {detailHref && (
          <Link className="text-sm font-medium text-accent underline-offset-2 hover:underline" to={detailHref} aria-label={`Pattern details: ${display.name}`}>
            Pattern details
          </Link>
        )}
        <Button variant="ghost" className="-ml-2" aria-expanded={open} aria-controls={evidenceId} onClick={() => setOpen((v) => !v)}>
          {open ? 'Hide evidence' : 'Show evidence'}
        </Button>
        {open && (
          <div id={evidenceId} className="mt-2">
            <EvidenceList evidence={pattern.evidence} names={names} redacted={redacted} />
          </div>
        )}
      </div>
    </article>
  );
}
