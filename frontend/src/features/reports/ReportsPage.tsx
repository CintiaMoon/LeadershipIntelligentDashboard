import { useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Field, selectClass } from '@/components/ui/Form';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateViews';
import { api, type ReportParams } from '@/services/api/endpoints';
import { useReport, useRuns } from '@/services/queries/hooks';
import { useSession } from '@/stores/session';
import { isReportType, REPORT_TYPES, type Report, type ReportType } from '@/types/report';
import type { RunSummary } from '@/types/api';
import { formatDateTime, formatScore } from '@/utils/presentation';

const runLabel = (r: RunSummary) => `${formatDateTime(r.calculatedAt)} · ${r.templateId} · ${formatScore(r.leadershipIntelligenceScore)}`;

/** Saves the bytes the backend generated under the name it gave them. */
function save(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function Downloads({ params }: { params: ReportParams }) {
  const [busy, setBusy] = useState<'pdf' | 'csv' | null>(null);
  const [failed, setFailed] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);
  const download = async (format: 'pdf' | 'csv') => {
    setBusy(format);
    setFailed(null);
    setDone(null);
    try {
      const file = await api.downloadReport(params, format);
      save(file.blob, file.filename);
      setDone(`Saved ${file.filename}`);
    } catch {
      setFailed(`The ${format.toUpperCase()} file could not be created. Try again.`);
    } finally {
      setBusy(null);
    }
  };
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Button onClick={() => void download('pdf')} disabled={busy !== null}>
        {busy === 'pdf' ? 'Creating the PDF' : 'Download PDF'}
      </Button>
      <Button onClick={() => void download('csv')} disabled={busy !== null}>
        {busy === 'csv' ? 'Creating the CSV' : 'Download CSV'}
      </Button>
      {done && <span role="status" className="text-xs text-ink-soft">{done}</span>}
      {failed && <span role="alert" className="text-xs text-risk-critical">{failed}</span>}
    </div>
  );
}

function ReportView({ report, params }: { report: Report; params: ReportParams }) {
  return (
    <Card>
      <CardHeader title={report.title} description={report.notice} action={<Downloads params={params} />} />
      <div className="space-y-8 p-5">
        {report.redacted && <p role="note" className="rounded-md bg-risk-medium-soft px-3 py-2 text-sm">Some values are withheld for your role.</p>}
        {report.sections.map((section) => (
          <section key={section.id} aria-labelledby={`report-${section.id}`}>
            <h3 id={`report-${section.id}`} className="text-sm font-semibold">{section.heading}</h3>
            {section.note && <p className="mt-0.5 text-sm text-ink-soft">{section.note}</p>}
            {section.rows.length > 0 && (
              <div className="mt-2 overflow-x-auto" role="region" aria-label={`${section.heading}, scrollable`} tabIndex={0}>
                <table className="w-full min-w-max text-sm">
                  <caption className="sr-only">{section.heading}</caption>
                  <thead>
                    <tr className="border-b border-line text-left text-xs text-ink-soft">
                      {section.columns.map((c) => (
                        <th key={c} scope="col" className="py-1.5 pr-4 font-medium">{c}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {section.rows.map((row, i) => (
                      <tr key={i} className="border-b border-line/60 align-top">
                        {row.map((cell, j) =>
                          j === 0 ? (
                            <th key={j} scope="row" className="py-1.5 pr-4 text-left font-normal">{cell}</th>
                          ) : (
                            <td key={j} className="py-1.5 pr-4 tabular-nums">{cell}</td>
                          ),
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        ))}
      </div>
    </Card>
  );
}

/**
 * Reports (decisions D59 to D64): choose a report and an assessment, read it on the page, and save it as a PDF or a CSV file.
 * The backend builds every report from the stored result, so the page, the PDF and the CSV always say the same thing.
 */
export function ReportsPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const organizationId = useSession((s) => s.organizationId);
  const runs = useRuns({ organizationId: organizationId ?? undefined, limit: 50 });
  const options = runs.data?.items ?? [];

  const typeParam = params.get('type');
  const type: ReportType = isReportType(typeParam) ? typeParam : 'LEADERSHIP_INTELLIGENCE';
  const comparison = type === 'HISTORICAL_COMPARISON';
  const run = params.get('run') ?? '';
  const base = params.get('base') ?? '';

  const [chosenType, setChosenType] = useState<ReportType>(type);
  const [chosenRun, setChosenRun] = useState(run);
  const [chosenBase, setChosenBase] = useState(base);
  const [errors, setErrors] = useState<{ run?: string; base?: string }>({});
  const chosenComparison = chosenType === 'HISTORICAL_COMPARISON';

  const request: ReportParams | undefined = run && (!comparison || base) ? { type, runId: run, baseRunId: comparison ? base : undefined } : undefined;
  const report = useReport(request);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const next: { run?: string; base?: string } = {};
    if (!chosenRun) next.run = chosenComparison ? 'Choose the later assessment' : 'Choose an assessment';
    if (chosenComparison && !chosenBase) next.base = 'Choose the earlier assessment';
    if (chosenComparison && chosenBase && chosenBase === chosenRun) next.run = 'Choose two different assessments';
    setErrors(next);
    if (next.run || next.base) return;
    const query = new URLSearchParams({ type: chosenType, run: chosenRun });
    if (chosenComparison) query.set('base', chosenBase);
    navigate(`/reports?${query.toString()}`);
  };

  const description = REPORT_TYPES.find((t) => t.type === chosenType)?.description;
  const picker = (id: string, label: string, value: string, set: (v: string) => void, error?: string) => (
    <Field id={id} label={label} error={error}>
      <select id={id} className={selectClass} value={value} aria-invalid={!!error} aria-describedby={error ? `${id}-error` : undefined} onChange={(e) => set(e.target.value)}>
        <option value="">Select&hellip;</option>
        {options.map((r) => (
          <option key={r.scoringRunId} value={r.scoringRunId}>{runLabel(r)}</option>
        ))}
      </select>
    </Field>
  );

  return (
    <div className="space-y-8">
      <PageHeader title="Reports" subtitle="A summary of a stored assessment to read, print or share. Every figure comes from the stored result; nothing is recalculated." />

      <Card className="p-5">
        <form onSubmit={submit} className="space-y-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field id="report-type" label="Report" help={description}>
              <select id="report-type" className={selectClass} value={chosenType} aria-describedby="report-type-help" onChange={(e) => setChosenType(e.target.value as ReportType)}>
                {REPORT_TYPES.map((t) => (
                  <option key={t.type} value={t.type}>{t.label}</option>
                ))}
              </select>
            </Field>
            {chosenComparison && picker('report-base', 'Earlier assessment', chosenBase, setChosenBase, errors.base)}
            {picker('report-run', chosenComparison ? 'Later assessment' : 'Assessment', chosenRun, setChosenRun, errors.run)}
          </div>
          <Button type="submit" variant="primary">Show report</Button>
          {runs.isError && <p className="text-xs text-risk-critical">The list of assessments could not be loaded.</p>}
        </form>
      </Card>

      {!request ? (
        <EmptyState title="Choose a report" description="Pick a report and an assessment, then show the report." />
      ) : report.isPending ? (
        <LoadingState label="Preparing the report" />
      ) : report.isError ? (
        <ErrorState error={report.error} onRetry={() => void report.refetch()} />
      ) : (
        <ReportView report={report.data} params={request} />
      )}
    </div>
  );
}
