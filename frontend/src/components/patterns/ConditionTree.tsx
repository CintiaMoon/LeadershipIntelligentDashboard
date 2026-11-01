import { Badge, type Tone } from '@/components/ui/Badge';
import type { Names } from '@/features/dashboard/display';
import type { ConditionNode } from '@/types/views';
import { OUTCOME_WORDS, toConditionView, type ConditionView, type Outcome } from './conditions';

const TONES: Record<Outcome, Tone> = { met: 'ok', 'not-met': 'neutral', unknown: 'medium' };
const MARK: Record<Outcome, string> = { met: '\u2713', 'not-met': '\u2715', unknown: '?' };

function Node({ view }: { view: ConditionView }) {
  return (
    <li>
      <div className="flex flex-wrap items-start gap-2 py-1 text-sm">
        <Badge tone={TONES[view.outcome]}>
          <span aria-hidden="true" className="mr-1">
            {MARK[view.outcome]}
          </span>
          {OUTCOME_WORDS[view.outcome]}
        </Badge>
        <span>{view.text}</span>
      </div>
      {view.children.length > 0 && (
        <ul className="ml-4 border-l border-line pl-4">
          {view.children.map((c, i) => (
            <Node key={i} view={c} />
          ))}
        </ul>
      )}
    </li>
  );
}

/** The conditions a pattern is made of, with whether each was met, exactly as evaluated by the backend. */
export function ConditionTree({ condition, names }: { condition: ConditionNode; names: Names }) {
  return (
    <ul aria-label="Matched conditions">
      <Node view={toConditionView(condition, names)} />
    </ul>
  );
}
