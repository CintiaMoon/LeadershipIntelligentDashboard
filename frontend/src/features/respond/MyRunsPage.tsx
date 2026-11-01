import { Link } from 'react-router-dom';
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { statusTone } from '@/components/ui/Form';
import { PageHeader } from '@/components/ui/PageHeader';
import { QuerySection } from '@/components/ui/QuerySection';
import { EmptyState } from '@/components/ui/StateViews';
import { useMyRuns } from '@/services/queries/hooks';
import { formatDate, humanizeCode } from '@/utils/presentation';

/** The assessments a respondent has been asked to answer (decision D48). */
export function MyRunsPage() {
  const runs = useMyRuns();
  return (
    <div className="space-y-6">
      <PageHeader title="My assessments" subtitle="The questionnaires you have been asked to answer. Your answers are anonymous: they are stored with your team, never with your name." />
      <QuerySection query={runs} label="your assessments">
        {() => {
          const items = runs.data ?? [];
          if (items.length === 0) {
            return <EmptyState title="Nothing to answer" description="You have not been asked to answer anything. If you expected a questionnaire, ask the person who runs the assessment to check that your e-mail address is on the list." />;
          }
          return (
            <ul className="grid gap-4 sm:grid-cols-2" aria-label="Your assessments">
              {items.map((r) => {
                const closed = r.runStatus === 'CLOSED';
                const action = r.myStatus === 'SUBMITTED' || closed ? 'View' : r.myStatus === 'IN_PROGRESS' ? 'Continue' : 'Start';
                return (
                  <li key={r.runId}>
                    <Card className="flex h-full flex-col gap-3 p-5">
                      <div>
                        <h2 className="text-base font-semibold">{r.name}</h2>
                        <p className="text-sm text-ink-soft">{r.questionnaireName}</p>
                      </div>
                      <div className="flex flex-wrap items-center gap-2 text-sm">
                        <Badge tone={statusTone(r.myStatus ?? '')}>{humanizeCode(r.myStatus)}</Badge>
                        {closed && <Badge tone="neutral">Run closed</Badge>}
                        <span className="text-ink-soft">{r.dueDate ? `Due ${formatDate(r.dueDate)}` : 'No due date'}</span>
                      </div>
                      <Link
                        to={`/respond/${r.runId}`}
                        aria-label={`${action} ${r.name}`}
                        className="mt-auto inline-flex min-h-9 w-fit items-center rounded-md bg-ink px-3.5 text-sm font-medium text-canvas hover:bg-ink/90"
                      >
                        {action}
                      </Link>
                    </Card>
                  </li>
                );
              })}
            </ul>
          );
        }}
      </QuerySection>
    </div>
  );
}
