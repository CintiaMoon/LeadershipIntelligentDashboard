import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Badge } from '@/components/ui/Badge';
import { Button, linkButtonClass } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { statusTone } from '@/components/ui/Form';
import { PageHeader } from '@/components/ui/PageHeader';
import { QuerySection } from '@/components/ui/QuerySection';
import { EmptyState } from '@/components/ui/StateViews';
import { useArchiveQuestionnaire, useDuplicateQuestionnaire, useQuestionnaires } from '@/services/queries/hooks';
import { formatDateTime } from '@/utils/presentation';
import { STATE_LABEL } from './model';

type Filter = 'ALL' | 'PUBLISHED' | 'DRAFTS' | 'ARCHIVED';

const FILTERS: { id: Filter; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'PUBLISHED', label: 'Published' },
  { id: 'DRAFTS', label: 'Drafts' },
  { id: 'ARCHIVED', label: 'Archived' },
];

function matches(filter: Filter, state: string | undefined): boolean {
  switch (filter) {
    case 'PUBLISHED':
      return state === 'PUBLISHED' || state === 'PUBLISHED_WITH_DRAFT';
    case 'DRAFTS':
      return state === 'DRAFT' || state === 'PUBLISHED_WITH_DRAFT';
    case 'ARCHIVED':
      return state === 'ARCHIVED';
    default:
      return true;
  }
}

/** The questionnaire library (decisions D47, D50): every questionnaire of the tenant, with its state and what can be done with it. */
export function QuestionnaireListPage() {
  const navigate = useNavigate();
  const questionnaires = useQuestionnaires();
  const duplicate = useDuplicateQuestionnaire();
  const archive = useArchiveQuestionnaire();
  const [filter, setFilter] = useState<Filter>('ALL');
  const [search, setSearch] = useState('');
  const needle = search.trim().toLowerCase();

  return (
    <div className="space-y-6">
      <PageHeader
        title="Questionnaires"
        subtitle="Versioned assessments, ready to deploy across teams. A published version never changes; editing it opens a new draft."
        actions={
          <Link to="/questionnaires/new" className={linkButtonClass}>
            New questionnaire
          </Link>
        }
      />
      <QuerySection query={questionnaires} label="the questionnaires">
        {() => {
          const items = questionnaires.data ?? [];
          if (items.length === 0) {
            return (
              <EmptyState
                title="No questionnaires yet"
                description="Create one from scratch, or start from the initial questionnaire and adjust it."
                action={
                  <Link to="/questionnaires/new" className={linkButtonClass}>
                    New questionnaire
                  </Link>
                }
              />
            );
          }
          const shown = items.filter((q) => matches(filter, q.state) && (!needle || (q.name ?? '').toLowerCase().includes(needle)));
          return (
            <Card>
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4">
                <label className="flex items-center gap-2 text-sm text-ink-soft">
                  <span>Search</span>
                  <input
                    type="search"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    placeholder="Questionnaire name"
                    className="min-h-9 w-56 rounded-md border border-line bg-surface px-2 text-sm text-ink"
                  />
                </label>
                <div role="group" aria-label="Show" className="flex gap-1 rounded-md border border-line bg-canvas p-1">
                  {FILTERS.map((f) => (
                    <button
                      key={f.id}
                      type="button"
                      aria-pressed={filter === f.id}
                      onClick={() => setFilter(f.id)}
                      className={`rounded px-3 py-1 text-sm font-medium ${filter === f.id ? 'bg-surface text-ink shadow-card' : 'text-ink-soft hover:text-ink'}`}
                    >
                      {f.label}
                    </button>
                  ))}
                </div>
              </div>
              {(duplicate.isError || archive.isError) && (
                <p role="alert" className="border-b border-line px-5 py-3 text-sm text-risk-critical">
                  The change could not be made. Reload the list and try again.
                </p>
              )}
              {shown.length === 0 ? (
                <p className="px-5 py-8 text-sm text-ink-soft">No questionnaire matches.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-max text-sm">
                    <caption className="sr-only">Questionnaires, newest first</caption>
                    <thead>
                      <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-faint">
                        <th scope="col" className="px-5 py-2 font-medium">Questionnaire</th>
                        <th scope="col" className="px-5 py-2 font-medium">Status</th>
                        <th scope="col" className="px-5 py-2 font-medium">Versions</th>
                        <th scope="col" className="px-5 py-2 font-medium">Updated</th>
                        <th scope="col" className="px-5 py-2 font-medium"><span className="sr-only">Actions</span></th>
                      </tr>
                    </thead>
                    <tbody>
                      {shown.map((q) => (
                        <tr key={q.id} className="border-b border-line/60">
                          <th scope="row" className="px-5 py-3 text-left font-medium">
                            <Link to={`/questionnaires/${q.id}`} className="hover:underline">
                              {q.name}
                            </Link>
                          </th>
                          <td className="px-5 py-3">
                            <Badge tone={q.state === 'ARCHIVED' ? 'neutral' : statusTone(q.state ?? '')}>{STATE_LABEL[q.state ?? ''] ?? q.state}</Badge>
                          </td>
                          <td className="px-5 py-3 text-ink-soft">
                            {q.publishedVersion ? `v${q.publishedVersion} published` : 'not published'}
                            {q.draftVersion ? `, v${q.draftVersion} draft` : ''}
                          </td>
                          <td className="px-5 py-3 tabular-nums text-ink-soft">{formatDateTime(q.updatedAt ?? q.createdAt)}</td>
                          <td className="px-5 py-3">
                            <div className="flex justify-end gap-1">
                              <Link to={`/questionnaires/${q.id}`} className="rounded-md px-2.5 py-1.5 text-sm font-medium text-accent-ink hover:bg-accent-soft">
                                {q.archived ? 'View' : 'Open'}
                              </Link>
                              <Button
                                variant="ghost"
                                disabled={duplicate.isPending}
                                aria-label={`Duplicate ${q.name}`}
                                onClick={() => duplicate.mutate(q.id as string, { onSuccess: (copy) => navigate(`/questionnaires/${copy.id}`) })}
                              >
                                Duplicate
                              </Button>
                              {!q.archived && (
                                <Button variant="ghost" disabled={archive.isPending} aria-label={`Archive ${q.name}`} onClick={() => archive.mutate(q.id as string)}>
                                  Archive
                                </Button>
                              )}
                            </div>
                          </td>
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
    </div>
  );
}
