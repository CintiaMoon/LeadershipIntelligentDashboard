import { Badge } from '@/components/ui/Badge';
import { severityTone } from '@/utils/labels';
import { humanizeCode } from '@/utils/presentation';

const SYMBOL: Record<string, string> = { CRITICAL: '\u25C6', HIGH: '\u25B2', MEDIUM: '\u25CF', LOW: '\u25CB' };

/** Severity is impact. It is always written out and given a shape, so colour is never the only signal (frontend-spec 20). */
export function SeverityBadge({ severity }: { severity: string }) {
  return (
    <Badge tone={severityTone(severity)}>
      <span aria-hidden="true" className="mr-1">
        {SYMBOL[severity] ?? '\u25CB'}
      </span>
      {humanizeCode(severity)}
    </Badge>
  );
}
