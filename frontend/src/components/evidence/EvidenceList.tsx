import type { Names } from '@/features/dashboard/display';
import type { Evidence } from '@/types/views';
import { formatRatio, formatScore, humanizeCode } from '@/utils/presentation';

function label(operandKey: string, names: Names): string {
  const [kind, id] = operandKey.split(':', 2);
  if (kind === 'signal' && id) return names.signal(id).name;
  if (kind === 'dimension' && id) return names.dimension(id).name;
  return humanizeCode(operandKey);
}

/** Evidence behind a finding. When the backend redacted the values for this role, only what it kept is shown. */
export function EvidenceList({ evidence, names, redacted }: { evidence: Evidence[]; names: Names; redacted: boolean }) {
  if (evidence.length === 0) return <p className="text-sm text-ink-soft">No evidence items were recorded.</p>;
  return (
    <div>
      {redacted && <p className="mb-2 text-xs text-ink-faint">Values are hidden for your role. The evidence items themselves are listed.</p>}
      <ul className="divide-y divide-line rounded-lg border border-line">
        {evidence.map((item) => (
          <li key={item.operandKey} className="grid gap-1 px-3 py-2 text-sm sm:grid-cols-[1fr_auto]">
            <div>
              <p className="font-medium">{label(item.operandKey, names)}</p>
              {item.sourceQuestionIds && item.sourceQuestionIds.length > 0 && (
                <p className="text-xs text-ink-soft">From: {item.sourceQuestionIds.map((q) => names.question(q).name).join(', ')}</p>
              )}
            </div>
            <div className="text-right text-sm tabular-nums">
              {item.numericValue != null ? <span>{formatScore(item.numericValue)}</span> : item.flag !== null && item.flag !== undefined ? <span>{typeof item.flag === 'boolean' ? (item.flag ? 'Present' : 'Absent') : humanizeCode(item.flag)}</span> : <span className="text-ink-faint">{redacted ? 'Hidden' : '\u2013'}</span>}
              {item.reliability != null && <p className="text-xs text-ink-faint">Reliability {formatRatio(item.reliability)}</p>}
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
