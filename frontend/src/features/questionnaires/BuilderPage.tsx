import { useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Field, FormError, inputClass, statusTone, textareaClass } from '@/components/ui/Form';
import { BackLink, PageHeader } from '@/components/ui/PageHeader';
import { LoadingState } from '@/components/ui/StateViews';
import { ErrorState } from '@/components/ui/StateViews';
import { ApiError } from '@/services/api/errors';
import { usePublishQuestionnaire, useQuestionnaire, useSaveQuestionnaire } from '@/services/queries/hooks';
import type { QuestionnaireDetail, QuestionnaireDocument } from '@/types/api';
import { QuestionCard } from './QuestionCard';
import { RuleCard } from './RuleCard';
import { KINDS, STATE_LABEL, moveItem, newQuestion, newRule, sameDocument, summarize, type QuestionKind } from './model';

/** What the server reported for a failed save or publish, one line per problem. */
function problemsOf(error: unknown): string[] {
  if (!(error instanceof ApiError) || !error.details) return [];
  return Object.entries(error.details)
    .filter(([key]) => key.startsWith('problem'))
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([, text]) => text);
}

/**
 * The questionnaire builder (decisions D47, D50). It edits a working copy of the open draft and shows what the server says is
 * still missing; the server checks every save and decides whether the questionnaire can be published. A published version is shown
 * read-only, and editing it opens the next draft.
 */
export function BuilderPage() {
  const { questionnaireId } = useParams();
  // a new key gives a fresh working copy when another questionnaire is opened
  return <Builder key={questionnaireId} questionnaireId={questionnaireId} />;
}

function Builder({ questionnaireId }: { questionnaireId: string | undefined }) {
  const navigate = useNavigate();
  const notes = (useLocation().state as { notes?: string[] } | null)?.notes ?? [];
  const query = useQuestionnaire(questionnaireId);
  const save = useSaveQuestionnaire();
  const publish = usePublishQuestionnaire();

  // The working copy is only what the user has changed and not yet saved; everything else is the server copy.
  const [edits, setEdits] = useState<QuestionnaireDocument | null>(null);
  const source: QuestionnaireDetail | undefined = query.data;
  const doc: QuestionnaireDocument = edits ?? source?.document ?? {};
  const dirty = edits !== null && source?.document !== undefined && !sameDocument(edits, source.document);
  const summary = summarize(doc);

  if (!source) {
    if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
    return <LoadingState label="Loading the questionnaire" />;
  }

  const readOnly = source.archived === true || source.versionStatus !== 'DRAFT';
  const questions = doc.questions ?? [];
  const rules = doc.rules ?? [];
  const update = (patch: Partial<QuestionnaireDocument>) => setEdits({ ...doc, ...patch });
  const busy = save.isPending || publish.isPending;
  const problems = source.problems ?? [];
  const failure = publish.error ?? save.error;

  const saveDraft = () => save.mutateAsync({ id: source.id as string, document: doc }).then(() => setEdits(null));
  const openNewDraft = () => saveDraft().catch(() => undefined);
  const publishNow = async () => {
    try {
      if (dirty) {
        await save.mutateAsync({ id: source.id as string, document: doc });
        setEdits(null);
      }
      await publish.mutateAsync(source.id as string);
    } catch {
      // the error is shown from the mutation state below
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        back={<BackLink to="/questionnaires">Back to the questionnaires</BackLink>}
        title={doc.name || 'Untitled questionnaire'}
        subtitle={`Version ${source.version} · ${readOnly ? 'read only' : 'draft'}. Set it up, then add and configure its questions.`}
        actions={
          readOnly ? (
            source.archived ? null : (
              <Button variant="primary" onClick={() => void openNewDraft()} disabled={busy}>
                Edit as a new draft
              </Button>
            )
          ) : (
            <div className="flex flex-wrap gap-2">
              <Button onClick={() => navigate('/questionnaires')} disabled={busy}>
                Cancel
              </Button>
              <Button onClick={() => void saveDraft().catch(() => undefined)} disabled={busy || !dirty}>
                {save.isPending && !publish.isPending ? 'Saving' : 'Save draft'}
              </Button>
              <Button variant="primary" onClick={() => void publishNow()} disabled={busy}>
                {publish.isPending ? 'Publishing' : 'Publish'}
              </Button>
            </div>
          )
        }
      />

      {notes.length > 0 && (
        <div role="note" className="rounded-lg border border-line bg-surface px-4 py-3 text-sm">
          <p className="font-medium">What the builder could not carry over from the initial questionnaire</p>
          <ul className="mt-1 list-disc space-y-0.5 pl-5 text-ink-soft">
            {notes.map((n) => (
              <li key={n}>{n}</li>
            ))}
          </ul>
        </div>
      )}
      {source.archived && (
        <p role="note" className="rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink-soft">
          This questionnaire is archived. It cannot be changed, and its published version stays available to explain past assessments.
        </p>
      )}
      {!source.archived && readOnly && (
        <p role="note" className="rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink-soft">
          This version is {STATE_LABEL[source.versionStatus === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT']?.toLowerCase()} and never changes. Editing opens a new draft.
        </p>
      )}
      {failure && <FormError error={failure} details={problemsOf(failure)} />}

      <div className="grid grid-cols-[minmax(0,1fr)] gap-6 lg:grid-cols-[minmax(0,1fr)_18rem]">
        <div className="min-w-0 space-y-6">
          <Card>
            <CardHeader title="Details" />
            <div className="space-y-4 p-5">
              <Field id="qd-name" label="Name">
                <input id="qd-name" type="text" className={inputClass} placeholder="e.g. Q1 Leadership 360" value={doc.name ?? ''} readOnly={readOnly} onChange={(e) => update({ name: e.target.value })} />
              </Field>
              <Field id="qd-description" label="Description">
                <textarea id="qd-description" rows={3} className={textareaClass} placeholder="What does this questionnaire measure, and who is it for?" value={doc.description ?? ''} readOnly={readOnly} onChange={(e) => update({ description: e.target.value })} />
              </Field>
            </div>
          </Card>

          <Card>
            <CardHeader title={`Questions (${questions.length})`} description="Each scored question feeds one dimension. Free text is collected and never scored." />
            <div className="space-y-4 p-5">
              {questions.length === 0 && <p className="text-sm text-ink-soft">No questions yet. Add the first one below.</p>}
              <ol className="space-y-4">
                {questions.map((q, i) => (
                  <QuestionCard
                    key={q.id}
                    question={q}
                    index={i}
                    count={questions.length}
                    readOnly={readOnly}
                    onChange={(next) => update({ questions: questions.map((x, j) => (j === i ? next : x)) })}
                    onMove={(direction) => update({ questions: moveItem(questions, i, i + direction) })}
                    onRemove={() => update({ questions: questions.filter((_, j) => j !== i) })}
                  />
                ))}
              </ol>
              {!readOnly && (
                <div role="group" aria-label="Add a question" className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                  {KINDS.map((k) => (
                    <Button key={k.kind} onClick={() => update({ questions: [...questions, newQuestion(k.kind as QuestionKind, questions)] })} className="justify-between">
                      <span>{k.label}</span>
                      <span aria-hidden="true">+</span>
                    </Button>
                  ))}
                </div>
              )}
            </div>
          </Card>

          <Card>
            <CardHeader
              title={`Rules (${rules.length})`}
              description="Optional. A rule detects a pattern in the answers and can lower a score and recommend an action. Thresholds and amounts you set here are your own and are not calibrated."
            />
            <div className="space-y-4 p-5">
              {rules.length === 0 && <p className="text-sm text-ink-soft">No rules. The questionnaire still scores its four dimensions.</p>}
              <ol className="space-y-4">
                {rules.map((r, i) => (
                  <RuleCard
                    key={r.id ?? `new-${i}`}
                    rule={r}
                    index={i}
                    questions={questions}
                    readOnly={readOnly}
                    onChange={(next) => update({ rules: rules.map((x, j) => (j === i ? next : x)) })}
                    onRemove={() => update({ rules: rules.filter((_, j) => j !== i) })}
                  />
                ))}
              </ol>
              {!readOnly && <Button onClick={() => update({ rules: [...rules, newRule(questions)] })}>Add rule</Button>}
            </div>
          </Card>
        </div>

        <aside aria-label="Summary" className="space-y-4 lg:sticky lg:top-6 lg:self-start">
          <Card className="p-5">
            <h2 className="text-base font-semibold">Summary</h2>
            <dl className="mt-3 space-y-2 text-sm">
              {[
                ['Questions', summary.questions],
                ['Required', summary.required],
                ['Dimensions covered', `${summary.dimensions} of 4`],
                ['Rules', summary.rules],
              ].map(([term, value]) => (
                <div key={term} className="flex justify-between">
                  <dt className="text-ink-soft">{term}</dt>
                  <dd className="font-medium tabular-nums">{value}</dd>
                </div>
              ))}
              <div className="flex justify-between">
                <dt className="text-ink-soft">Anonymous</dt>
                <dd className="font-medium">Yes</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-ink-soft">State</dt>
                <dd>
                  <Badge tone={statusTone(source.state ?? '')}>{STATE_LABEL[source.state ?? ''] ?? source.state}</Badge>
                </dd>
              </div>
            </dl>
            <p className="mt-4 text-xs text-ink-soft">Publish to make this questionnaire available for assessment runs. Drafts stay editable.</p>
          </Card>
          {!readOnly && (
            <Card className="p-5" aria-live="polite">
              <h2 className="text-base font-semibold">Before you publish</h2>
              {dirty && <p className="mt-2 text-xs text-ink-soft">You have unsaved changes. Save the draft to refresh these checks.</p>}
              {problems.length === 0 ? (
                <p className="mt-2 text-sm text-ok">Nothing is missing.</p>
              ) : (
                <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-ink-soft">
                  {problems.map((p) => (
                    <li key={p}>{p}</li>
                  ))}
                </ul>
              )}
            </Card>
          )}
        </aside>
      </div>
    </div>
  );
}
