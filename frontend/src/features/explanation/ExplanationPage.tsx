import { useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { ResponsesPanel } from '@/components/responses/ResponsesPanel';
import { BackLink, PageHeader } from '@/components/ui/PageHeader';
import { ErrorState, LoadingState } from '@/components/ui/StateViews';
import { useCatalog, useExplanation, useRun } from '@/services/queries/hooks';
import { parseTrace } from '@/types/trace';
import { parseResponses } from '@/types/views';
import { formatDateTime } from '@/utils/presentation';
import { makeNames } from '@/features/dashboard/display';
import { STAGES } from './stages';

/** One experience that follows the calculation from the inputs to the final score, straight from the stored trace. */
export function ExplanationPage() {
  const { runId } = useParams();
  const explanation = useExplanation(runId);
  const run = useRun(runId);
  const catalog = useCatalog(runId);
  const names = useMemo(() => makeNames(catalog.data), [catalog.data]);
  const parsed = useMemo(() => {
    try {
      return { trace: explanation.data ? parseTrace(explanation.data.trace) : undefined, error: null as Error | null };
    } catch (e) {
      return { trace: undefined, error: e as Error };
    }
  }, [explanation.data]);

  const responses = useMemo(() => {
    try {
      return { value: parseResponses(run.data?.responses), error: null as Error | null };
    } catch (e) {
      return { value: null, error: e as Error };
    }
  }, [run.data]);

  if (explanation.isPending) return <LoadingState label="Loading the explanation" />;
  if (explanation.isError) return <ErrorState error={explanation.error} onRetry={() => void explanation.refetch()} />;
  if (parsed.error || !parsed.trace) return <ErrorState error={parsed.error} />;
  const trace = parsed.trace;

  return (
    <div className="space-y-8">
      <PageHeader
        back={<BackLink to="/dashboard">Back to the dashboard</BackLink>}
        title="How the Leadership Intelligence Score was calculated"
        subtitle={
          <>
            {run.data?.scope === 'TEAM' && <>Team {run.data.teamId} &middot; </>}
            Run of {formatDateTime(run.data?.calculatedAt)} &middot; configuration {trace.header.configurationVersion} &middot; engine {trace.header.engineVersion}, algorithm{' '}
            {trace.header.algorithmVersion}
          </>
        }
      />
      {explanation.data.redacted && (
        <p role="note" className="rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink-soft">
          Question-level detail is hidden for your role. Every score and every finding is shown.
        </p>
      )}

      <nav aria-label="Calculation steps">
        <ol className="flex flex-wrap gap-2">
          {STAGES.map((s, i) => (
            <li key={s.id}>
              <a href={`#step-${s.id}`} className="inline-flex items-center gap-2 rounded-full border border-line bg-surface px-3 py-1 text-sm text-ink-soft hover:border-accent hover:text-ink">
                <span className="grid size-5 place-items-center rounded-full bg-accent-soft text-xs font-semibold text-accent-ink">{i + 1}</span>
                {s.title}
              </a>
            </li>
          ))}
        </ol>
      </nav>

      <ol className="space-y-4">
        {STAGES.map((s, i) => (
          <li key={s.id}>
            <details id={`step-${s.id}`} open={s.id === 'final' || s.id === 'adjusted'} className="group rounded-xl border border-line bg-surface shadow-card">
              <summary className="flex cursor-pointer items-center gap-3 px-5 py-4">
                <span aria-hidden="true" className="grid size-7 place-items-center rounded-full bg-accent text-sm font-semibold text-white">
                  {i + 1}
                </span>
                <h2 className="text-base font-semibold">{s.title}</h2>
              </summary>
              <div className="border-t border-line px-5 py-4">
                <s.Body trace={trace} names={names} runId={runId as string} />
              </div>
            </details>
          </li>
        ))}
      </ol>
      {responses.error ? (
        <ErrorState error={responses.error} />
      ) : (
        responses.value && <ResponsesPanel responses={responses.value} names={names} insufficient={run.data?.scoreStatus === 'INSUFFICIENT_DATA'} />
      )}
      {run.data?.notice && <p className="text-xs text-ink-soft">{run.data.notice}</p>}
    </div>
  );
}
