import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from '@/components/ui/Badge';
import { linkButtonClass } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { statusTone } from '@/components/ui/Form';
import { PageHeader } from '@/components/ui/PageHeader';
import { QuerySection } from '@/components/ui/QuerySection';
import { EmptyState } from '@/components/ui/StateViews';
import { useAssessmentRuns } from '@/services/queries/hooks';
import { useSession } from '@/stores/session';
import { formatDate, formatDateTime, humanizeCode } from '@/utils/presentation';

type Filter = 'ALL' | 'OPEN' | 'CLOSED';

/** Assessment runs of the chosen organization (decision D48): who is asked, how far they are, and the result once closed. */
export function RunListPage() {
  const organizationId = useSession((s) => s.organizationId);
  const [filter, setFilter] = useState<Filter>('ALL');
  const runs = useAssessmentRuns({ organizationId: organizationId ?? undefined, status: filter === 'ALL' ? undefined : filter }, !!organizationId);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Assessment runs"
        subtitle="A run asks the people of some teams to answer one published questionnaire. Closing it scores the answers."
        actions={
          <Link to="/assessment-runs/new" className={linkButtonClass}>
            Start a run
          </Link>
        }
      />
      {!organizationId ? (
        <EmptyState title="Choose an organization" description="Runs belong to an organization. Choose one at the top of the page." />
      ) : (
        <QuerySection query={runs} label="the runs">
          {() => {
            const items = runs.data ?? [];
            return (
              <Card>
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4">
                  <p className="text-sm text-ink-soft" role="status">
                    {items.length} {items.length === 1 ? 'run' : 'runs'} in {organizationId}
                  </p>
                  <div role="group" aria-label="Show" className="flex gap-1 rounded-md border border-line bg-canvas p-1">
                    {(['ALL', 'OPEN', 'CLOSED'] as const).map((f) => (
                      <button
                        key={f}
                        type="button"
                        aria-pressed={filter === f}
                        onClick={() => setFilter(f)}
                        className={`rounded px-3 py-1 text-sm font-medium ${filter === f ? 'bg-surface text-ink shadow-card' : 'text-ink-soft hover:text-ink'}`}
                      >
                        {f === 'ALL' ? 'All' : humanizeCode(f)}
                      </button>
                    ))}
                  </div>
                </div>
                {items.length === 0 ? (
                  <p className="px-5 py-8 text-sm text-ink-soft">No runs yet. Start one to ask a team to answer a published questionnaire.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-max text-sm">
                      <caption className="sr-only">Assessment runs, newest first</caption>
                      <thead>
                        <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-faint">
                          <th scope="col" className="px-5 py-2 font-medium">Run</th>
                          <th scope="col" className="px-5 py-2 font-medium">Questionnaire</th>
                          <th scope="col" className="px-5 py-2 font-medium">Status</th>
                          <th scope="col" className="px-5 py-2 font-medium">Finished</th>
                          <th scope="col" className="px-5 py-2 font-medium">Due</th>
                          <th scope="col" className="px-5 py-2 font-medium">Started</th>
                        </tr>
                      </thead>
                      <tbody>
                        {items.map((r) => (
                          <tr key={r.id} className="border-b border-line/60">
                            <th scope="row" className="px-5 py-3 text-left font-medium">
                              <Link to={`/assessment-runs/${r.id}`} className="hover:underline">
                                {r.name}
                              </Link>
                            </th>
                            <td className="px-5 py-3 text-ink-soft">
                              {r.questionnaireName} <span className="text-ink-faint">v{r.questionnaireVersion}</span>
                            </td>
                            <td className="px-5 py-3">
                              <Badge tone={statusTone(r.status ?? '')}>{humanizeCode(r.status)}</Badge>
                            </td>
                            <td className="px-5 py-3 tabular-nums">
                              {r.submitted} of {r.participants}
                            </td>
                            <td className="px-5 py-3 tabular-nums text-ink-soft">{r.dueDate ? formatDate(r.dueDate) : 'No due date'}</td>
                            <td className="px-5 py-3 tabular-nums text-ink-soft">{formatDateTime(r.createdAt)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </Card>
            );
          }}
        </QuerySection>
      )}
    </div>
  );
}
