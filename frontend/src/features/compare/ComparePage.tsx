import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { z } from 'zod';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { BackLink, PageHeader } from '@/components/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateViews';
import { makeNames } from '@/features/dashboard/display';
import { useCatalog, useComparison, useRuns } from '@/services/queries/hooks';
import { useSession } from '@/stores/session';
import type { RunSummary } from '@/types/api';
import { comparabilityReason, interpretationTone } from '@/utils/labels';
import { formatChange, formatDateTime, formatScore, humanizeCode } from '@/utils/presentation';

const formSchema = z
  .object({ base: z.string().min(1, 'Choose the earlier assessment'), run: z.string().min(1, 'Choose the later assessment') })
  .refine((v) => v.base !== v.run, { path: ['run'], message: 'Choose two different assessments' });
type FormValues = z.infer<typeof formSchema>;

function runLabel(r: RunSummary): string {
  return `${formatDateTime(r.calculatedAt)} \u00B7 ${r.templateId} \u00B7 ${formatScore(r.leadershipIntelligenceScore)}`;
}

function Side({ title, run }: { title: string; run: RunSummary }) {
  return (
    <Card className="p-5">
      <p className="text-xs font-medium uppercase tracking-wide text-ink-soft">{title}</p>
      <p className="mt-1 text-4xl font-semibold tabular-nums">{formatScore(run.leadershipIntelligenceScore)}</p>
      <p className="mt-1 flex flex-wrap items-center gap-2 text-xs text-ink-soft">
        {run.interpretation && <Badge tone={interpretationTone(run.interpretation)}>{humanizeCode(run.interpretation)}</Badge>}
        {formatDateTime(run.calculatedAt)}
      </p>
    </Card>
  );
}

export function ComparePage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const organizationId = useSession((s) => s.organizationId);
  const base = params.get('base') ?? undefined;
  const run = params.get('run') ?? undefined;

  const runs = useRuns({ organizationId: organizationId ?? undefined, limit: 50 });
  const comparison = useComparison(base, run);
  const catalog = useCatalog(run);
  const names = makeNames(catalog.data);
  const form = useForm<FormValues>({ resolver: zodResolver(formSchema), values: { base: base ?? '', run: run ?? '' } });

  const options = runs.data?.items ?? [];
  const c = comparison.data;

  const differences: [string, string | number | undefined, string | number | undefined][] = c?.base && c.run
    ? [
        ['Scoring configuration', c.base.configurationVersion, c.run.configurationVersion],
        ['Configuration hash', c.base.configurationHash?.slice(0, 19), c.run.configurationHash?.slice(0, 19)],
        ['Algorithm version', c.base.algorithmVersion, c.run.algorithmVersion],
        ['Engine version', c.base.engineVersion, c.run.engineVersion],
        ['Assessment template', c.base.templateId, c.run.templateId],
        ['Template version', c.base.templateVersion, c.run.templateVersion],
        ['Organization', c.base.organizationId, c.run.organizationId],
      ]
    : [];

  return (
    <div className="space-y-8">
      <PageHeader back={<BackLink to="/dashboard">Back to the dashboard</BackLink>} title="Compare assessments" subtitle="A change is only shown between assessments that were scored the same way." />

      <Card className="p-5">
        <form onSubmit={form.handleSubmit((v) => navigate(`/compare?base=${v.base}&run=${v.run}`))} className="grid gap-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end" noValidate>
          {(['base', 'run'] as const).map((field) => (
            <label key={field} className="text-sm">
              <span className="mb-1 block text-ink-soft">{field === 'base' ? 'Earlier assessment' : 'Later assessment'}</span>
              <select className="min-h-9 w-full rounded-md border border-line bg-surface px-2" aria-invalid={!!form.formState.errors[field]} {...form.register(field)}>
                <option value="">Select&hellip;</option>
                {options.map((r) => (
                  <option key={r.scoringRunId} value={r.scoringRunId}>
                    {runLabel(r)}
                  </option>
                ))}
              </select>
              {form.formState.errors[field] && (
                <span role="alert" className="mt-1 block text-xs text-risk-critical">
                  {form.formState.errors[field]?.message}
                </span>
              )}
            </label>
          ))}
          <Button type="submit" variant="primary">
            Compare
          </Button>
        </form>
        {runs.isError && <p className="mt-3 text-xs text-risk-critical">The list of assessments could not be loaded.</p>}
      </Card>

      {!base || !run ? (
        <EmptyState title="Choose two assessments" description="Pick an earlier and a later assessment to see how they differ." />
      ) : comparison.isPending ? (
        <LoadingState label="Comparing the assessments" />
      ) : comparison.isError ? (
        <ErrorState error={comparison.error} onRetry={() => void comparison.refetch()} />
      ) : c?.base && c.run ? (
        <>
          <div className="grid gap-4 sm:grid-cols-2">
            <Side title="Earlier" run={c.base} />
            <Side title="Later" run={c.run} />
          </div>

          {c.comparable ? (
            <Card className="p-5">
              <p className="text-sm text-ink-soft">
                Same configuration, algorithm and template version, so these are comparable. Score change: <strong className="text-lg text-ink tabular-nums">{formatChange(c.scoreChange)}</strong>
              </p>
            </Card>
          ) : (
            <div role="note" className="rounded-xl border border-risk-medium/40 bg-risk-medium-soft px-5 py-4 text-sm">
              <p className="font-semibold">These assessments are not comparable</p>
              <p className="mt-1 text-ink-soft">{(c.notComparableReasons ?? []).map(comparabilityReason).join('; ')}. No score change is shown, because the difference would not be like for like.</p>
            </div>
          )}

          <Card>
            <CardHeader title="What differs between the two" description="Rows that differ are marked" />
            <div className="overflow-x-auto p-5">
              <table className="w-full min-w-max text-sm">
                <caption className="sr-only">Scoring basis of the two assessments</caption>
                <thead>
                  <tr className="border-b border-line text-left text-xs text-ink-soft">
                    <th scope="col" className="py-1.5 pr-4 font-medium">Basis</th>
                    <th scope="col" className="py-1.5 pr-4 font-medium">Earlier</th>
                    <th scope="col" className="py-1.5 pr-4 font-medium">Later</th>
                    <th scope="col" className="py-1.5 font-medium">
                      <span className="sr-only">Differs</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {differences.map(([label, a, b]) => (
                    <tr key={label} className="border-b border-line/60">
                      <th scope="row" className="py-1.5 pr-4 text-left font-normal">{label}</th>
                      <td className="py-1.5 pr-4 font-mono text-xs">{a ?? '\u2013'}</td>
                      <td className="py-1.5 pr-4 font-mono text-xs">{b ?? '\u2013'}</td>
                      <td className="py-1.5">{a !== b && <Badge tone="medium">Differs</Badge>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <Card>
            <CardHeader title="Dimensions" description="Adjusted scores" />
            <div className="overflow-x-auto p-5">
              <table className="w-full min-w-max text-sm">
                <caption className="sr-only">Dimension scores in both assessments</caption>
                <thead>
                  <tr className="border-b border-line text-left text-xs text-ink-soft">
                    <th scope="col" className="py-1.5 pr-4 font-medium">Dimension</th>
                    <th scope="col" className="py-1.5 pr-4 font-medium">Earlier</th>
                    <th scope="col" className="py-1.5 pr-4 font-medium">Later</th>
                    <th scope="col" className="py-1.5 font-medium">Change</th>
                  </tr>
                </thead>
                <tbody>
                  {(c.dimensions ?? []).map((d) => (
                    <tr key={d.dimension} className="border-b border-line/60">
                      <th scope="row" className="py-1.5 pr-4 text-left font-normal">{names.dimension(d.dimension ?? '').name}</th>
                      <td className="py-1.5 pr-4 tabular-nums">{formatScore(d.baseScore)}</td>
                      <td className="py-1.5 pr-4 tabular-nums">{formatScore(d.score)}</td>
                      <td className="py-1.5 tabular-nums">{c.comparable ? formatChange(d.change) : 'Not comparable'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <Card>
            <CardHeader title="Patterns" description="Detected in the later assessment compared with the earlier one" />
            <div className="grid gap-6 p-5 sm:grid-cols-3">
              {(
                [
                  ['New', c.patterns?.appeared],
                  ['Resolved', c.patterns?.resolved],
                  ['Persistent', c.patterns?.persistent],
                ] as const
              ).map(([title, ids]) => (
                <div key={title}>
                  <h3 className="text-sm font-semibold">{title}</h3>
                  {(ids ?? []).length === 0 ? (
                    <p className="mt-1 text-sm text-ink-soft">None</p>
                  ) : (
                    <ul className="mt-1 space-y-1 text-sm">
                      {(ids ?? []).map((id) => (
                        <li key={id}>{names.pattern(id).name}</li>
                      ))}
                    </ul>
                  )}
                </div>
              ))}
            </div>
          </Card>
          {c.notice && <p className="text-xs text-ink-soft">{c.notice}</p>}
        </>
      ) : null}
    </div>
  );
}
