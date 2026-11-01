import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Field, FormError, inputClass, selectClass } from '@/components/ui/Form';
import { BackLink, PageHeader } from '@/components/ui/PageHeader';
import { QuerySection } from '@/components/ui/QuerySection';
import { EmptyState } from '@/components/ui/StateViews';
import { useQuestionnaires, useStartRun, useTeams } from '@/services/queries/hooks';
import { useSession } from '@/stores/session';

/**
 * Starts a run (decision D48): one published questionnaire, some teams of the chosen organization and an optional due date. The run
 * pins the version published now, so later edits cannot change it, and it asks every active person of the teams.
 */
export function StartRunPage() {
  const organizationId = useSession((s) => s.organizationId);
  const navigate = useNavigate();
  const questionnaires = useQuestionnaires();
  const teams = useTeams(organizationId ?? undefined, !!organizationId);
  const start = useStartRun();
  const [name, setName] = useState('');
  const [questionnaireId, setQuestionnaireId] = useState('');
  const [teamIds, setTeamIds] = useState<string[]>([]);
  const [dueDate, setDueDate] = useState('');
  const [problems, setProblems] = useState<{ name?: string; questionnaire?: string; teams?: string }>({});

  const submit = () => {
    const next: typeof problems = {};
    if (!name.trim()) next.name = 'Enter a name for the run.';
    if (!questionnaireId) next.questionnaire = 'Choose a published questionnaire.';
    if (teamIds.length === 0) next.teams = 'Choose at least one team.';
    setProblems(next);
    if (Object.keys(next).length > 0 || !organizationId) return;
    start.mutate(
      { name: name.trim(), organizationId, questionnaireId, teamIds, dueDate: dueDate || undefined },
      { onSuccess: (detail) => navigate(`/assessment-runs/${detail.run?.id}`) },
    );
  };

  return (
    <div className="space-y-6">
      <PageHeader
        back={<BackLink to="/assessment-runs">Back to the runs</BackLink>}
        title="Start a run"
        subtitle="Choose what to ask and whom. The questionnaire version published now is pinned to the run."
      />
      {!organizationId ? (
        <EmptyState title="Choose an organization" description="Runs belong to an organization. Choose one at the top of the page." />
      ) : (
        <QuerySection query={questionnaires} label="the questionnaires">
          {() => (
            <QuerySection query={teams} label="the teams">
              {() => {
                const published = (questionnaires.data ?? []).filter((q) => (q.state === 'PUBLISHED' || q.state === 'PUBLISHED_WITH_DRAFT') && !q.archived);
                const teamList = teams.data ?? [];
                if (published.length === 0) {
                  return <EmptyState title="No published questionnaire" description="Publish a questionnaire first. Drafts and archived questionnaires cannot be used." />;
                }
                if (teamList.length === 0) {
                  return <EmptyState title="No teams in this organization" description="Add teams and the people on them first." />;
                }
                return (
                  <Card className="max-w-2xl p-6">
                    <form
                      noValidate
                      className="space-y-5"
                      onSubmit={(e) => {
                        e.preventDefault();
                        submit();
                      }}
                    >
                      <Field id="run-name" label="Name" error={problems.name}>
                        <input id="run-name" type="text" className={inputClass} placeholder="e.g. Q4 team pulse" value={name} onChange={(e) => setName(e.target.value)} aria-invalid={!!problems.name} aria-describedby="run-name-error" />
                      </Field>
                      <Field id="run-questionnaire" label="Questionnaire" error={problems.questionnaire}>
                        <select
                          id="run-questionnaire"
                          className={selectClass}
                          value={questionnaireId}
                          onChange={(e) => setQuestionnaireId(e.target.value)}
                          aria-invalid={!!problems.questionnaire}
                          aria-describedby="run-questionnaire-error"
                        >
                          <option value="">Choose...</option>
                          {published.map((q) => (
                            <option key={q.id} value={q.id}>
                              {q.name} (v{q.publishedVersion})
                            </option>
                          ))}
                        </select>
                      </Field>
                      <fieldset className="min-w-0" aria-describedby="run-teams-error">
                        <legend className="text-sm font-medium">Teams</legend>
                        <ul className="mt-2 space-y-1">
                          {teamList.map((t) => (
                            <li key={t.id}>
                              <label className="flex items-center gap-2 text-sm">
                                <input
                                  type="checkbox"
                                  checked={teamIds.includes(t.id as string)}
                                  onChange={(e) => setTeamIds(e.target.checked ? [...teamIds, t.id as string] : teamIds.filter((x) => x !== t.id))}
                                />
                                <span>{t.name}</span>
                                <span className="text-ink-soft">
                                  ({t.activePeople} active {t.activePeople === 1 ? 'person' : 'people'})
                                </span>
                              </label>
                            </li>
                          ))}
                        </ul>
                        <p id="run-teams-error" role="alert" className="mt-1 text-xs text-risk-critical">
                          {problems.teams}
                        </p>
                      </fieldset>
                      <Field id="run-due" label="Due date (optional)" help="It is shown to respondents. Nothing closes automatically; you close the run.">
                        <input id="run-due" type="date" className={`${inputClass} !w-auto`} value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
                      </Field>
                      <FormError error={start.error} />
                      <div className="flex items-center gap-3">
                        <Button type="submit" variant="primary" disabled={start.isPending}>
                          {start.isPending ? 'Starting' : 'Start run'}
                        </Button>
                        <Button onClick={() => navigate('/assessment-runs')} disabled={start.isPending}>
                          Cancel
                        </Button>
                      </div>
                    </form>
                  </Card>
                );
              }}
            </QuerySection>
          )}
        </QuerySection>
      )}
    </div>
  );
}
