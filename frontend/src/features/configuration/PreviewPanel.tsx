import { Badge } from '@/components/ui/Badge';
import { Card, CardHeader } from '@/components/ui/Card';
import type { ConfigDistribution, ConfigFixtureResult, ConfigPreview, ConfigPreviewSummary } from '@/types/api';
import { formatScore, humanizeCode } from '@/utils/presentation';

function DistributionRow({ label, d }: { label: string; d: ConfigDistribution | undefined }) {
  const bands = Object.entries(d?.bands ?? {});
  return (
    <tr className="border-b border-line/60">
      <th scope="row" className="px-5 py-2 text-left font-medium">{label}</th>
      <td className="px-5 py-2 tabular-nums">{d?.scored ?? 0}</td>
      <td className="px-5 py-2 tabular-nums">{d?.minimum === undefined ? '—' : formatScore(d.minimum)}</td>
      <td className="px-5 py-2 tabular-nums">{d?.median === undefined ? '—' : formatScore(d.median)}</td>
      <td className="px-5 py-2 tabular-nums">{d?.maximum === undefined ? '—' : formatScore(d.maximum)}</td>
      <td className="px-5 py-2 text-ink-soft">{bands.length === 0 ? '—' : bands.map(([band, n]) => `${humanizeCode(band)} ${n}`).join(', ')}</td>
    </tr>
  );
}

/** The fixtures of a layer scored with a draft in place, with the reason each one fails (decision D56). */
export function FixtureResults({ fixtures }: { fixtures: ConfigFixtureResult[] }) {
  if (fixtures.length === 0) {
    return <p className="px-5 py-4 text-sm text-ink-soft">This layer has no fixtures, so nothing blocks approval. Add some on the layer page.</p>;
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-max text-sm">
        <caption className="sr-only">Fixtures with this version in place</caption>
        <thead>
          <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-faint">
            <th scope="col" className="px-5 py-2 font-medium">Fixture</th>
            <th scope="col" className="px-5 py-2 font-medium">Result</th>
            <th scope="col" className="px-5 py-2 font-medium">Why</th>
          </tr>
        </thead>
        <tbody>
          {fixtures.map((f) => (
            <tr key={f.fixtureId} className="border-b border-line/60 align-top">
              <th scope="row" className="px-5 py-2 text-left font-medium">{f.name}</th>
              <td className="px-5 py-2">
                <Badge tone={f.passed ? 'ok' : 'critical'}>{f.passed ? 'Passes' : 'Fails'}</Badge>
              </td>
              <td className="px-5 py-2 text-ink-soft">
                {f.passed ? (
                  f.outcome?.score === undefined ? '' : `overall score ${formatScore(f.outcome.score)}`
                ) : (
                  <ul className="list-disc pl-4">
                    {(f.failures ?? []).map((x) => (
                      <li key={x}>{x}</li>
                    ))}
                  </ul>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function SummaryLine({ summary }: { summary: ConfigPreviewSummary | undefined }) {
  if (!summary) return null;
  return (
    <p className="px-5 py-3 text-sm text-ink-soft">
      {summary.fixturesPassed} of {summary.fixtures} fixtures pass.{' '}
      {summary.assessmentsUsed
        ? `${summary.assessments} recent ${summary.assessments === 1 ? 'assessment was' : 'assessments were'} scored before and after: ${summary.scoresChanged} ${summary.scoresChanged === 1 ? 'score changes' : 'scores change'}, ${summary.patternsAppearing} ${summary.patternsAppearing === 1 ? 'pattern appears' : 'patterns appear'}, ${summary.patternsDisappearing} disappear.`
        : 'Recent assessments were not used, so this is a preview against fixtures only.'}
    </p>
  );
}

/**
 * The preview of a draft (decision D55): what it does to the fixtures and, when allowed, to the tenant's recent assessments. It shows
 * numbers, bands and the ids of patterns and penalties; no answer is ever part of it.
 */
export function PreviewPanel({ preview }: { preview: ConfigPreview }) {
  const summary = preview.summary;
  const assessments = (preview.items ?? []).filter((i) => i.kind === 'ASSESSMENT');
  const changed = assessments.filter((i) => (i.changes ?? []).length > 0);
  return (
    <Card aria-label="Preview">
      <CardHeader title="Preview" description="Scored in memory, with this version in place and as things are now. Nothing is stored." />
      <SummaryLine summary={summary} />
      {summary?.assessmentsUsed && (
        <div className="overflow-x-auto border-t border-line">
          <table className="w-full min-w-max text-sm">
            <caption className="sr-only">Overall scores of recent assessments, before and after</caption>
            <thead>
              <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-faint">
                <th scope="col" className="px-5 py-2 font-medium">Scores</th>
                <th scope="col" className="px-5 py-2 font-medium">Scored</th>
                <th scope="col" className="px-5 py-2 font-medium">Lowest</th>
                <th scope="col" className="px-5 py-2 font-medium">Median</th>
                <th scope="col" className="px-5 py-2 font-medium">Highest</th>
                <th scope="col" className="px-5 py-2 font-medium">Bands</th>
              </tr>
            </thead>
            <tbody>
              <DistributionRow label="Now" d={summary.before} />
              <DistributionRow label="With this version" d={summary.after} />
            </tbody>
          </table>
        </div>
      )}
      <div className="border-t border-line">
        <h3 className="px-5 pt-3 text-sm font-semibold">Fixtures</h3>
        <FixtureResults fixtures={preview.fixtures ?? []} />
      </div>
      {changed.length > 0 && (
        <div className="overflow-x-auto border-t border-line">
          <h3 className="px-5 pt-3 text-sm font-semibold">Assessments that change</h3>
          <table className="w-full min-w-max text-sm">
            <caption className="sr-only">Recent assessments whose result changes</caption>
            <thead>
              <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-faint">
                <th scope="col" className="px-5 py-2 font-medium">Assessment</th>
                <th scope="col" className="px-5 py-2 font-medium">Organization</th>
                <th scope="col" className="px-5 py-2 font-medium">Score</th>
                <th scope="col" className="px-5 py-2 font-medium">What changes</th>
              </tr>
            </thead>
            <tbody>
              {changed.slice(0, 50).map((i) => (
                <tr key={i.name} className="border-b border-line/60 align-top">
                  <th scope="row" className="px-5 py-2 text-left font-mono text-xs font-normal">{i.name}</th>
                  <td className="px-5 py-2">{i.organizationId}</td>
                  <td className="px-5 py-2 tabular-nums">
                    {i.before?.score === undefined ? '—' : formatScore(i.before.score)} <span aria-hidden="true">&rarr;</span>
                    <span className="sr-only"> to </span> {i.after?.score === undefined ? '—' : formatScore(i.after.score)}
                  </td>
                  <td className="px-5 py-2 text-ink-soft">
                    <ul className="list-disc pl-4">
                      {(i.changes ?? []).map((c) => (
                        <li key={c}>{c}</li>
                      ))}
                    </ul>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}
