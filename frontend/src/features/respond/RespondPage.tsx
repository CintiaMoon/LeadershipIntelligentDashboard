import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { FormError, statusTone } from '@/components/ui/Form';
import { BackLink, PageHeader } from '@/components/ui/PageHeader';
import { QuerySection } from '@/components/ui/QuerySection';
import { useMyRun, useSaveDraft, useSubmitAnswers } from '@/services/queries/hooks';
import type { RespondentQuestion, RespondentRunDetail } from '@/types/api';
import { formatDate, humanizeCode } from '@/utils/presentation';
import { AnswerInput } from './AnswerInput';

type Answers = Record<string, unknown>;

/** Whether an answer counts as given: a selection that is empty or a text that is blank does not. */
export function isAnswered(value: unknown): boolean {
  if (value === undefined || value === null) return false;
  if (typeof value === 'string') return value.trim() !== '';
  if (Array.isArray(value)) return value.length > 0;
  return true;
}

function toRequest(questions: RespondentQuestion[], answers: Answers) {
  return {
    answers: questions.map((q) => ({
      questionId: q.id as string,
      answer: (isAnswered(answers[q.id as string]) ? answers[q.id as string] : null) as never,
    })),
  };
}

function Form({ detail }: { detail: RespondentRunDetail }) {
  const runId = detail.runId as string;
  const questions = detail.questions ?? [];
  const save = useSaveDraft();
  const submit = useSubmitAnswers();
  const [answers, setAnswers] = useState<Answers>(detail.answers ?? {});
  const [missing, setMissing] = useState<string[]>([]);
  const [savedNotice, setSavedNotice] = useState(false);
  const readOnly = detail.myStatus === 'SUBMITTED' || detail.runStatus === 'CLOSED';

  const set = (id: string, value: unknown) => {
    setAnswers({ ...answers, [id]: value });
    setMissing(missing.filter((m) => m !== id));
    setSavedNotice(false);
  };

  const saveDraft = () =>
    save.mutateAsync({ id: runId, request: toRequest(questions, answers) }).then(() => {
      setSavedNotice(true);
    });

  const submitAll = async () => {
    const open = questions.filter((q) => q.required && !isAnswered(answers[q.id as string])).map((q) => q.id as string);
    setMissing(open);
    if (open.length > 0) {
      document.getElementById(`a-${open[0]}`)?.closest('fieldset')?.scrollIntoView?.({ block: 'center' });
      return;
    }
    try {
      await save.mutateAsync({ id: runId, request: toRequest(questions, answers) });
      await submit.mutateAsync(runId);
    } catch {
      // shown from the mutation state below
    }
  };

  if (detail.myStatus === 'SUBMITTED') {
    return (
      <Card className="p-6" role="status">
        <h2 className="text-base font-semibold">Thank you, your answers are submitted</h2>
        <p className="mt-1 text-sm text-ink-soft">
          They are stored with your team only, not with your name, and cannot be changed or read back one by one. Results are only ever shown for groups.
        </p>
      </Card>
    );
  }

  const failure = submit.error ?? save.error;
  return (
    <form
      noValidate
      className="space-y-6"
      onSubmit={(e) => {
        e.preventDefault();
        void submitAll();
      }}
    >
      {detail.runStatus === 'CLOSED' && (
        <p role="note" className="rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink-soft">
          This run is closed. You can read the questions but no longer answer them.
        </p>
      )}
      {missing.length > 0 && (
        <p role="alert" className="rounded-lg border border-risk-critical/30 bg-risk-critical-soft px-4 py-3 text-sm text-risk-critical">
          {missing.length} required {missing.length === 1 ? 'question needs' : 'questions need'} an answer before you can submit.
        </p>
      )}
      <ol className="space-y-5">
        {questions.map((q, i) => (
          <li key={q.id}>
            <Card className="p-5">
              <p className="mb-2 font-mono text-xs text-ink-faint" aria-hidden="true">
                {String(i + 1).padStart(2, '0')}
              </p>
              <AnswerInput question={q} value={answers[q.id as string]} readOnly={readOnly} invalid={missing.includes(q.id as string)} onChange={(v) => set(q.id as string, v)} />
            </Card>
          </li>
        ))}
      </ol>
      <FormError error={failure} />
      {!readOnly && (
        <div className="flex flex-wrap items-center gap-3">
          <Button type="button" onClick={() => void saveDraft().catch(() => undefined)} disabled={save.isPending || submit.isPending}>
            {save.isPending && !submit.isPending ? 'Saving' : 'Save draft'}
          </Button>
          <Button type="submit" variant="primary" disabled={save.isPending || submit.isPending}>
            {submit.isPending ? 'Submitting' : 'Submit answers'}
          </Button>
          <span role="status" className="text-sm text-ink-soft">
            {savedNotice ? 'Draft saved. You can come back and continue later.' : ''}
          </span>
        </div>
      )}
    </form>
  );
}

/** Answering one questionnaire (decision D48): save a draft and continue later, then submit once. */
export function RespondPage() {
  const { runId } = useParams();
  const query = useMyRun(runId);
  return (
    <div className="space-y-6">
      <QuerySection query={query} label="the questionnaire">
        {() => {
          const detail = query.data;
          if (!detail) return null;
          return (
            <>
              <PageHeader
                back={<BackLink to="/respond">Back to my assessments</BackLink>}
                title={detail.name ?? 'Assessment'}
                subtitle={`${detail.questionnaireName}${detail.dueDate ? ` · due ${formatDate(detail.dueDate)}` : ''}`}
                actions={<Badge tone={statusTone(detail.myStatus ?? '')}>{humanizeCode(detail.myStatus)}</Badge>}
              />
              <p role="note" className="rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink-soft">
                Your answers are anonymous. They are stored with your team, never with your name, and results are only shown for groups. Free-text comments are
                collected but never scored, so please do not write anything that identifies you or a colleague.
              </p>
              <Form key={detail.runId} detail={detail} />
            </>
          );
        }}
      </QuerySection>
    </div>
  );
}
