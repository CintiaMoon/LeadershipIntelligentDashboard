import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Badge } from '@/components/ui/Badge';
import { Button, secondaryLinkClass } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { FormError, statusTone } from '@/components/ui/Form';
import { BackLink, PageHeader } from '@/components/ui/PageHeader';
import { QuerySection } from '@/components/ui/QuerySection';
import { useAssessmentRun, useCloseRun } from '@/services/queries/hooks';
import { formatDate, formatDateTime, humanizeCode } from '@/utils/presentation';

/**
 * One run (decision D48): progress per team, who has not finished and, for an open run, the way to close and score it. The list of
 * people says whether each has finished and never shows an answer; the notice beside it says what it can reveal in a small team.
 */
export function RunDetailPage() {
  const { runId } = useParams();
  const query = useAssessmentRun(runId);
  const close = useCloseRun();
  const [confirming, setConfirming] = useState(false);

  return (
    <div className="space-y-6">
      <QuerySection query={query} label="the run">
        {() => {
          const detail = query.data;
          const run = detail?.run;
          if (!detail || !run) return null;
          const open = run.status === 'OPEN';
          const teams = detail.teams ?? [];
          const participants = detail.participants ?? [];
          const unfinished = participants.filter((p) => p.status !== 'SUBMITTED');
          return (
            <>
              <PageHeader
                back={<BackLink to="/assessment-runs">Back to the runs</BackLink>}
                title={run.name ?? 'Run'}
                subtitle={`${run.questionnaireName} v${run.questionnaireVersion} · ${run.submitted} of ${run.participants} finished${run.dueDate ? ` · due ${formatDate(run.dueDate)}` : ''}`}
                actions={<Badge tone={statusTone(run.status ?? '')}>{humanizeCode(run.status)}</Badge>}
              />

              {open ? (
                <Card className="p-5">
                  <h2 className="text-base font-semibold">Close and score</h2>
                  <p className="mt-1 text-sm text-ink-soft">
                    Closing stops new answers and scores the submitted ones once, with the questionnaire version pinned to this run. It cannot be undone.
                  </p>
                  {confirming ? (
                    <div className="mt-3 flex flex-wrap items-center gap-2">
                      <p className="text-sm font-medium">{unfinished.length} of {participants.length} people have not finished. Close the run anyway?</p>
                      <Button variant="primary" disabled={close.isPending} onClick={() => close.mutate(run.id as string, { onSuccess: () => setConfirming(false) })}>
                        {close.isPending ? 'Scoring' : 'Close the run'}
                      </Button>
                      <Button onClick={() => setConfirming(false)} disabled={close.isPending}>
                        Keep it open
                      </Button>
                    </div>
                  ) : (
                    <Button className="mt-3" variant="primary" onClick={() => setConfirming(true)}>
                      Close and score
                    </Button>
                  )}
                  <div className="mt-3">
                    <FormError error={close.error} />
                  </div>
                </Card>
              ) : (
                <Card className="p-5">
                  <h2 className="text-base font-semibold">Result</h2>
                  <p className="mt-1 text-sm text-ink-soft">Closed {formatDateTime(run.closedAt)}. The answers were scored once, as answers of each team.</p>
                  {run.scoringRunId && (
                    <div className="mt-3 flex flex-wrap gap-2">
                      <Link to={`/runs/${run.scoringRunId}/explanation`} className={secondaryLinkClass}>
                        How the score was calculated
                      </Link>
                      <Link to="/dashboard" className={secondaryLinkClass}>
                        Open the dashboard
                      </Link>
                    </div>
                  )}
                </Card>
              )}

              <Card>
                <CardHeader title="Progress by team" />
                <div className="overflow-x-auto">
                  <table className="w-full min-w-max text-sm">
                    <caption className="sr-only">Progress by team</caption>
                    <thead>
                      <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-faint">
                        <th scope="col" className="px-5 py-2 font-medium">Team</th>
                        <th scope="col" className="px-5 py-2 font-medium">Not started</th>
                        <th scope="col" className="px-5 py-2 font-medium">In progress</th>
                        <th scope="col" className="px-5 py-2 font-medium">Finished</th>
                        <th scope="col" className="px-5 py-2 font-medium">Of</th>
                      </tr>
                    </thead>
                    <tbody>
                      {teams.map((t) => (
                        <tr key={t.teamId} className="border-b border-line/60">
                          <th scope="row" className="px-5 py-2 text-left font-medium">{t.teamName}</th>
                          <td className="px-5 py-2 tabular-nums">{t.notStarted}</td>
                          <td className="px-5 py-2 tabular-nums">{t.inProgress}</td>
                          <td className="px-5 py-2 tabular-nums">{t.submitted}</td>
                          <td className="px-5 py-2 tabular-nums text-ink-soft">{t.total}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Card>

              <Card>
                <CardHeader title="Who has finished" description="Reading this list is recorded." />
                <p role="note" className="border-b border-line px-5 py-3 text-sm text-ink-soft">
                  {detail.notice}
                </p>
                <div className="overflow-x-auto">
                  <table className="w-full min-w-max text-sm">
                    <caption className="sr-only">People asked to answer, and whether each has finished</caption>
                    <thead>
                      <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-faint">
                        <th scope="col" className="px-5 py-2 font-medium">Name</th>
                        <th scope="col" className="px-5 py-2 font-medium">E-mail address</th>
                        <th scope="col" className="px-5 py-2 font-medium">Team</th>
                        <th scope="col" className="px-5 py-2 font-medium">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {participants.map((p) => (
                        <tr key={p.email} className="border-b border-line/60">
                          <th scope="row" className="px-5 py-2 text-left font-medium">{p.name}</th>
                          <td className="px-5 py-2 text-ink-soft">{p.email}</td>
                          <td className="px-5 py-2 text-ink-soft">{teams.find((t) => t.teamId === p.teamId)?.teamName ?? p.teamId}</td>
                          <td className="px-5 py-2">
                            <Badge tone={statusTone(p.status ?? '')}>{humanizeCode(p.status)}</Badge>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Card>
            </>
          );
        }}
      </QuerySection>
    </div>
  );
}
