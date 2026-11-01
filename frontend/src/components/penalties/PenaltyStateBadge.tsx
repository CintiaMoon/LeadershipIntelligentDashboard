import { Badge, type Tone } from '@/components/ui/Badge';
import { humanizeCode } from '@/utils/presentation';

// Exactly four states exist (decision D7). A candidate penalty is not an applied one: the words say which it is.
const TONES: Record<string, Tone> = { APPLIED: 'high', SUPPRESSED: 'medium', INELIGIBLE: 'neutral', NOT_TRIGGERED: 'neutral' };
const WORDS: Record<string, string> = { APPLIED: 'Applied', SUPPRESSED: 'Suppressed', INELIGIBLE: 'Ineligible', NOT_TRIGGERED: 'Not triggered' };

export function PenaltyStateBadge({ status }: { status: string }) {
  return <Badge tone={TONES[status] ?? 'neutral'}>{WORDS[status] ?? humanizeCode(status)}</Badge>;
}
