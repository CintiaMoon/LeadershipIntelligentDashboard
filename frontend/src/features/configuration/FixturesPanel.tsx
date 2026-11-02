import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Field, FormError, inputClass, textareaClass } from '@/components/ui/Form';
import { QuerySection } from '@/components/ui/QuerySection';
import { useAddConfigFixture, useConfigFixtures, useDeleteConfigFixture, useReplaceConfigFixture } from '@/services/queries/configuration';
import type { ConfigFixture } from '@/types/api';
import { formatDateTime } from '@/utils/presentation';
import { detailsOf } from './ApprovalPanel';

const SAMPLE_ANSWERS = '[\n  { "questionId": "Q01", "answer": 4 }\n]';
const SAMPLE_EXPECTATIONS = '{\n  "status": "VALID",\n  "overall": { "min": 60, "max": 80 },\n  "patternsNotMatched": []\n}';

function FixtureForm({ layerId, fixture, onDone }: { layerId: string; fixture?: ConfigFixture; onDone: () => void }) {
  const add = useAddConfigFixture();
  const replace = useReplaceConfigFixture();
  const [name, setName] = useState(fixture?.name ?? '');
  const [answers, setAnswers] = useState(fixture ? JSON.stringify(fixture.answers, null, 2) : SAMPLE_ANSWERS);
  const [expectations, setExpectations] = useState(fixture ? JSON.stringify(fixture.expectations, null, 2) : SAMPLE_EXPECTATIONS);
  const [problems, setProblems] = useState<{ name?: string; answers?: string; expectations?: string }>({});
  const saving = add.isPending || replace.isPending;
  const error = add.error ?? replace.error;
  const idp = fixture ? `fx-${fixture.id}` : 'fx-new';

  const submit = () => {
    const next: typeof problems = {};
    if (!name.trim()) next.name = 'Enter a name.';
    let parsedAnswers: unknown;
    let parsedExpectations: unknown;
    try {
      parsedAnswers = JSON.parse(answers);
      if (!Array.isArray(parsedAnswers)) next.answers = 'The answers are a list: [ { "questionId": ..., "answer": ... } ].';
    } catch {
      next.answers = 'The answers are not valid JSON.';
    }
    try {
      parsedExpectations = JSON.parse(expectations);
      if (typeof parsedExpectations !== 'object' || parsedExpectations === null || Array.isArray(parsedExpectations)) next.expectations = 'The expectations are an object between { and }.';
    } catch {
      next.expectations = 'The expectations are not valid JSON.';
    }
    setProblems(next);
    if (Object.keys(next).length > 0) return;
    const spec = { name: name.trim(), answers: parsedAnswers as never, expectations: parsedExpectations as never };
    const done = { onSuccess: onDone };
    if (fixture) replace.mutate({ layerId, id: fixture.id as string, spec }, done);
    else add.mutate({ layerId, spec }, done);
  };

  return (
    <form
      noValidate
      className="space-y-4 border-t border-line p-5"
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
    >
      <h3 className="text-sm font-semibold">{fixture ? 'Edit the fixture' : 'Add a fixture'}</h3>
      <Field id={`${idp}-name`} label="Name" error={problems.name}>
        <input id={`${idp}-name`} type="text" className={inputClass} value={name} onChange={(e) => setName(e.target.value)} aria-invalid={!!problems.name} aria-describedby={`${idp}-name-error`} />
      </Field>
      <Field id={`${idp}-answers`} label="Answers (JSON)" error={problems.answers} help="One answer per question, as the questions of the configuration take them.">
        <textarea id={`${idp}-answers`} rows={6} spellCheck={false} className={`${textareaClass} font-mono text-xs`} value={answers} onChange={(e) => setAnswers(e.target.value)} aria-invalid={!!problems.answers} aria-describedby={`${idp}-answers-help ${idp}-answers-error`} />
      </Field>
      <Field
        id={`${idp}-expectations`}
        label="What must hold (JSON)"
        error={problems.expectations}
        help="Optional parts: status, overall {min, max}, dimensions {NAME: {min, max}}, patternsMatched, patternsNotMatched. Ranges, not exact scores."
      >
        <textarea id={`${idp}-expectations`} rows={6} spellCheck={false} className={`${textareaClass} font-mono text-xs`} value={expectations} onChange={(e) => setExpectations(e.target.value)} aria-invalid={!!problems.expectations} aria-describedby={`${idp}-expectations-help ${idp}-expectations-error`} />
      </Field>
      <FormError error={error} details={detailsOf(error)} />
      <div className="flex gap-2">
        <Button type="submit" variant="primary" disabled={saving}>
          {saving ? 'Saving' : 'Save fixture'}
        </Button>
        <Button onClick={onDone} disabled={saving}>
          Cancel
        </Button>
      </div>
    </form>
  );
}

/**
 * The fixtures of a layer (decision D56): representative answers with what the scoring must show. Approval of any version of the
 * layer is refused while one fails. Reading is open to everyone who may read configuration; changing needs {@code canChange}.
 */
export function FixturesPanel({ layerId, canChange }: { layerId: string | undefined; canChange: boolean }) {
  const fixtures = useConfigFixtures(layerId);
  const remove = useDeleteConfigFixture();
  const [editing, setEditing] = useState<string | 'new' | null>(null);
  const [confirming, setConfirming] = useState<string | null>(null);

  if (!layerId) {
    return (
      <Card aria-label="Fixtures">
        <CardHeader title="Fixtures" />
        <p className="px-5 py-4 text-sm text-ink-soft">This layer has no version yet. Create the first draft to add fixtures to it.</p>
      </Card>
    );
  }
  return (
    <Card aria-label="Fixtures">
      <CardHeader
        title="Fixtures"
        description="Representative test cases. A version cannot be approved while one of them fails."
        action={
          canChange && editing === null ? (
            <Button onClick={() => setEditing('new')}>Add a fixture</Button>
          ) : undefined
        }
      />
      <QuerySection query={fixtures} label="the fixtures">
        {() => {
          const items = fixtures.data ?? [];
          return (
            <>
              {items.length === 0 ? (
                <p className="px-5 py-4 text-sm text-ink-soft">No fixtures yet, so nothing blocks approval.</p>
              ) : (
                <ul className="divide-y divide-line">
                  {items.map((f) => (
                    <li key={f.id}>
                      {editing === f.id && canChange ? (
                        <FixtureForm layerId={layerId} fixture={f} onDone={() => setEditing(null)} />
                      ) : (
                        <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-3">
                          <div>
                            <p className="font-medium">{f.name}</p>
                            <p className="text-xs text-ink-soft">
                              {(f.answers as unknown[] | undefined)?.length ?? 0} answers &middot; by {f.createdBy}, {formatDateTime(f.updatedAt)}
                            </p>
                          </div>
                          {canChange && (
                            <div className="flex gap-2">
                              <Button variant="ghost" aria-label={`Edit fixture ${f.name}`} onClick={() => setEditing(f.id as string)}>
                                Edit
                              </Button>
                              {confirming === f.id ? (
                                <>
                                  <Button
                                    variant="primary"
                                    disabled={remove.isPending}
                                    onClick={() => remove.mutate({ layerId, id: f.id as string }, { onSuccess: () => setConfirming(null) })}
                                  >
                                    Delete {f.name}
                                  </Button>
                                  <Button onClick={() => setConfirming(null)}>Keep it</Button>
                                </>
                              ) : (
                                <Button variant="ghost" aria-label={`Delete fixture ${f.name}`} onClick={() => setConfirming(f.id as string)}>
                                  Delete
                                </Button>
                              )}
                            </div>
                          )}
                        </div>
                      )}
                    </li>
                  ))}
                </ul>
              )}
              {canChange && editing === 'new' && <FixtureForm layerId={layerId} onDone={() => setEditing(null)} />}
              {remove.error && (
                <div className="px-5 pb-4">
                  <FormError error={remove.error} />
                </div>
              )}
            </>
          );
        }}
      </QuerySection>
    </Card>
  );
}
