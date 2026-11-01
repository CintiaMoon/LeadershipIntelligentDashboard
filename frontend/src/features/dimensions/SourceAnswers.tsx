import type { RunAnswers } from '@/types/api';

type AnswerView = NonNullable<RunAnswers['answers']>[number];

export function answerText(answer: unknown): string {
  if (answer === null || answer === undefined) return 'No answer';
  if (typeof answer === 'boolean') return answer ? 'Yes' : 'No';
  // a question where several options may be chosen: the options, in the sorted order the backend stores them
  if (Array.isArray(answer)) return answer.length === 0 ? 'No option chosen' : answer.map(String).join(' + ');
  return String(answer);
}

const SMALL_GROUP = 'Groups this small are never shown, to protect the people who answered.';

/**
 * The stored answer or answers behind one contribution (decision D42, D45). Member answers arrive as one group with a
 * count and sorted values, never one by one and never with an order that could point to a person; a group below the
 * minimum arrives with its count only and is described here, not guessed at.
 */
export function SourceAnswers({ views }: { views: AnswerView[] }) {
  const items = views.length > 0 ? views : [{ questionId: '' } as AnswerView];
  return (
    <>
      {items.map((view, i) => (
        <p key={`${view.scope}-${view.teamId ?? ''}-${i}`}>
          <Single view={view} />
        </p>
      ))}
      <span className="block text-xs text-ink-faint">Opening an answer is recorded in the access log.</span>
    </>
  );
}

function Single({ view }: { view: AnswerView }) {
  if (view.scope === 'MEMBER') {
    if (view.suppressed) {
      return (
        <span>
          <strong>{view.count ?? 0}</strong> <span className="text-xs text-ink-soft">responses, too few to show. {SMALL_GROUP}</span>
        </span>
      );
    }
    return (
      <>
        <span className="text-xs text-ink-soft">{view.count} responses, in sorted order and not in the order given: </span>
        <strong>{(view.values ?? []).map(answerText).join(', ')}</strong>
      </>
    );
  }
  if (view.scope === 'TEAM') {
    return (
      <>
        <span className="text-xs text-ink-soft">Team {view.teamId}: </span>
        <strong>{answerText(view.answer)}</strong>
        <Declared count={view.declaredRespondents} />
      </>
    );
  }
  return (
    <>
      <span className="text-xs text-ink-soft">Answer given: </span>
      <strong>{answerText(view.answer)}</strong>
      <Declared count={view.declaredRespondents} />
    </>
  );
}

function Declared({ count }: { count?: number }) {
  if (!count) return null;
  return <span className="text-xs text-ink-soft"> &middot; a figure for {count} people, as stated by the sender and not verified</span>;
}
