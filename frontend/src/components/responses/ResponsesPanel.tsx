import { Badge } from '@/components/ui/Badge';
import { Card, CardHeader } from '@/components/ui/Card';
import type { Names } from '@/features/dashboard/display';
import type { Responses } from '@/types/views';
import { humanizeCode } from '@/utils/presentation';

const th = 'py-2 pr-4 text-left text-xs font-medium uppercase tracking-wide text-ink-soft';

const SCOPE: Record<string, string> = { MEMBER: 'Individual responses', TEAM: 'Team figures', ORGANIZATION: 'Organization figure' };
const METHOD: Record<string, string> = { MEDIAN: 'Median', MEAN: 'Mean' };
const SOURCE: Record<string, string> = {
  OBSERVED: 'Counted',
  DECLARED: 'Stated by the sender, not verified',
  UNKNOWN: 'Not known',
};

interface Props {
  responses: Responses;
  names: Names;
  /** The scope has no score because of too few responses. */
  insufficient?: boolean;
}

/**
 * How the answers of each question were combined (decision D45). Only counts and the method are shown: the answers of
 * individual people never are, and a group smaller than the configured minimum was not used at all.
 */
export function ResponsesPanel({ responses, names, insufficient = false }: Props) {
  const questions = responses.questions;
  if (questions.length === 0) return null;
  const small = questions.filter((q) => q.belowMinimum);
  const minimum = questions[0]?.minimum;

  return (
    <Card aria-labelledby="responses-heading">
      <CardHeader
        title="How the answers were combined"
        description="Answers from several people are combined question by question. What each person answered is never shown here."
      />
      <div className="space-y-4 px-5 py-4">
        {small.length > 0 && (
          <p role="note" className="rounded-lg border border-risk-medium/30 bg-risk-medium-soft px-4 py-3 text-sm text-ink">
            {small.length === 1 ? '1 question had' : `${small.length} questions had`} fewer than {minimum} responses, so {small.length === 1 ? 'it was' : 'they were'} not used.
            Groups this small are never shown, to protect the people who answered.
            {insufficient && ' Too little of the assessment remained, so no score is shown for this scope.'}
          </p>
        )}
        {insufficient && small.length === 0 && (
          <p role="note" className="rounded-lg border border-line bg-canvas px-4 py-3 text-sm text-ink-soft">No score is shown for this scope because too little data was available.</p>
        )}
        <div className="overflow-x-auto">
          <table className="w-full min-w-max text-sm">
            <caption className="sr-only">How the answers of each question were combined</caption>
            <thead>
              <tr className="border-b border-line">
                <th scope="col" className={th}>Question</th>
                <th scope="col" className={th}>Answers from</th>
                <th scope="col" className={th}>Combined by</th>
                <th scope="col" className={th}>Responses</th>
                <th scope="col" className={th}>Count is</th>
                <th scope="col" className={th}>Used</th>
              </tr>
            </thead>
            <tbody>
              {questions.map((q) => (
                <tr key={q.questionId} className="border-b border-line/60">
                  <th scope="row" className="py-1.5 pr-4 text-left font-normal">{names.question(q.questionId).name}</th>
                  <td className="py-1.5 pr-4">{SCOPE[q.scope] ?? humanizeCode(q.scope)}</td>
                  <td className="py-1.5 pr-4">{METHOD[q.method] ?? humanizeCode(q.method)}</td>
                  <td className="py-1.5 pr-4 tabular-nums">{q.respondents ?? 'Not known'}</td>
                  <td className="py-1.5 pr-4">{SOURCE[q.participation] ?? humanizeCode(q.participation)}</td>
                  <td className="py-1.5 pr-4">{q.belowMinimum ? <Badge tone="medium">Too few responses</Badge> : <Badge tone="ok">Yes</Badge>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {minimum !== undefined && <p className="text-xs text-ink-faint">A question needs at least {minimum} responses to be used.</p>}
      </div>
    </Card>
  );
}
