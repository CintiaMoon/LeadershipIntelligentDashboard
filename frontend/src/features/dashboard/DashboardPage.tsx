import { lazy, Suspense } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ResponsesPanel } from '@/components/responses/ResponsesPanel';
import { LisHero } from '@/components/score/LisHero';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateViews';
import { QuerySection } from '@/components/ui/QuerySection';
import { useSession } from '@/stores/session';
import { SEVERITIES } from '@/utils/labels';
import { DimensionOverview } from './DimensionOverview';
import { PatternOverview } from './PatternOverview';
import { PenaltyOverview } from './PenaltyOverview';
import { RecommendationsPanel } from './RecommendationsPanel';
import { RiskOverview } from './RiskOverview';
import { ScopeSwitcher } from './ScopeSwitcher';
import { VersionInfo } from './VersionInfo';
import { countBySeverity, type GroupBy } from './selectors';
import { useDashboardData } from './useDashboardData';

// the five trend charts sit below the fold and carry most of the chart code, so they load after the first paint
const TrendsSection = lazy(() => import('./TrendsSection').then((m) => ({ default: m.TrendsSection })));

const GROUPS: GroupBy[] = ['dimension', 'severity', 'confidence'];

export function DashboardPage() {
  const organizationId = useSession((s) => s.organizationId);
  const [params, setParams] = useSearchParams();
  const templateParam = params.get('template');
  const severityParam = params.get('severity');
  const severity = SEVERITIES.includes(severityParam as (typeof SEVERITIES)[number]) ? severityParam : null;
  const groupParam = params.get('group') as GroupBy | null;
  const groupBy: GroupBy = groupParam && GROUPS.includes(groupParam) ? groupParam : 'dimension';

  const teamParam = params.get('team');

  const data = useDashboardData(organizationId, templateParam, teamParam);
  const update = (change: Record<string, string | null>) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        for (const [k, v] of Object.entries(change)) {
          if (v === null) next.delete(k);
          else next.set(k, v);
        }
        return next;
      },
      { replace: true },
    );

  if (!organizationId) {
    return <EmptyState title="No organization available" description="Your account is not linked to an organization yet." />;
  }
  if (data.dashboard.isPending) return <LoadingState label="Loading the dashboard" />;
  if (data.dashboard.isError) return <ErrorState error={data.dashboard.error} onRetry={() => void data.dashboard.refetch()} />;
  if (!data.latest) {
    return (
      <div className="space-y-6">
        <PageHeader organizationId={organizationId} />
        <EmptyState title="No scored assessments yet" description="Results appear here as soon as an assessment has been scored for this organization." />
        {data.dashboard.data.notice && <p className="text-xs text-ink-soft">{data.dashboard.data.notice}</p>}
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader organizationId={organizationId} team={data.team?.teamId}>
        <div className="flex flex-wrap items-center gap-4">
          <ScopeSwitcher teams={data.teamRuns} value={data.team?.teamId ?? null} onChange={(team) => update({ team })} />
          {data.latestRuns.length > 1 && (
            <label className="flex items-center gap-2 text-sm text-ink-soft">
              <span>Assessment template</span>
              <select
                className="min-h-9 rounded-md border border-line bg-surface px-2 text-sm text-ink"
                value={data.latest.templateId}
                onChange={(e) => update({ template: e.target.value, team: null })}
              >
                {data.latestRuns.map((r) => (
                  <option key={r.templateId} value={r.templateId}>
                    {r.templateId}
                  </option>
                ))}
              </select>
            </label>
          )}
        </div>
      </PageHeader>

      {data.scoped && (
        <p role="note" className="rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink-soft">
          Showing team {data.team?.teamId}. History, trends and comparison with the previous assessment are available for the whole organization only. Teams are
          never ranked against each other.
        </p>
      )}

      {data.scoped && !data.runId ? (
        <EmptyState
          title="Not enough responses to score this team"
          description="A question needs enough responses before it is used, and this team's answers fell short on a required question. Groups this small are never shown, to protect the people who answered."
          action={
            <button type="button" className="text-sm font-medium text-accent underline-offset-2 hover:underline" onClick={() => update({ team: null })}>
              Back to the whole organization
            </button>
          }
        />
      ) : (
        <TeamOrOrganization data={data} severity={severity} groupBy={groupBy} update={update} />
      )}
    </div>
  );
}

type Data = ReturnType<typeof useDashboardData>;

function TeamOrOrganization({
  data,
  severity,
  groupBy,
  update,
}: {
  data: Data;
  severity: string | null;
  groupBy: GroupBy;
  update: (change: Record<string, string | null>) => void;
}) {
  const { parsed, names } = data;
  const history = data.trends.data?.points ?? [];
  const dimensionHistory = Object.fromEntries(
    ['TEAM_HEALTH', 'DELIVERY_STABILITY', 'TECHNICAL_RISK', 'LEADERSHIP_LOAD'].map((d) => [
      d,
      history.map((p) => p.dimensions?.find((x) => x.dimension === d)?.adjustedScore),
    ]),
  );
  const actionTitles = Object.fromEntries((data.recommendations.data?.recommendations ?? []).map((r) => [r.key ?? '', r.title ?? '']));
  const redacted = data.patterns.data?.redacted ?? true;

  return (
    <>
      <QuerySection query={data.run} label="the score">
        {() =>
          data.run.data && (
            <LisHero
              result={data.run.data}
              summary={data.scoped ? undefined : data.latest}
              bands={data.catalog.data?.lisBands ?? []}
              history={history.map((p) => p.run?.leadershipIntelligenceScore)}
              templateId={data.latest?.templateId}
            />
          )
        }
      </QuerySection>

      {parsed.error ? (
        <ErrorState error={parsed.error} />
      ) : (
        <>
          <QuerySection query={data.run} label="dimensions">
            {() =>
              data.run.data && (
                <DimensionOverview
                  result={data.run.data}
                  details={parsed.details}
                  patterns={parsed.patterns}
                  penalties={parsed.penalties}
                  names={names}
                  history={dimensionHistory}
                  comparison={data.comparison.data}
                  redacted={redacted}
                />
              )
            }
          </QuerySection>

          <QuerySection query={data.patterns} label="patterns">
            {() => (
              <>
                <RiskOverview counts={countBySeverity(parsed.patterns)} active={severity} onToggle={(s) => update({ severity: severity === s ? null : s })} />
                <PatternOverview
                  patterns={parsed.patterns}
                  penalties={parsed.penalties}
                  names={names}
                  actionTitles={actionTitles}
                  redacted={redacted}
                  runId={data.runId}
                  groupBy={groupBy}
                  onGroupBy={(by) => update({ group: by === 'dimension' ? null : by })}
                  severity={severity}
                  onClearSeverity={() => update({ severity: null })}
                />
              </>
            )}
          </QuerySection>

          <div className="grid gap-8 xl:grid-cols-2">
            <QuerySection query={data.penalties} label="penalties">
              {() => <PenaltyOverview penalties={parsed.penalties} effectiveByTarget={parsed.effectiveByTarget} names={names} runId={data.runId} />}
            </QuerySection>
            <QuerySection query={data.recommendations} label="recommendations">
              {() => data.recommendations.data && <RecommendationsPanel data={data.recommendations.data} names={names} />}
            </QuerySection>
          </div>
          {parsed.responses && <ResponsesPanel responses={parsed.responses} names={names} insufficient={data.run.data?.scoreStatus === 'INSUFFICIENT_DATA'} />}
        </>
      )}

      {!data.scoped && (
        <QuerySection query={data.trends} label="trends">
          {() =>
            data.trends.data && (
              <Suspense fallback={<LoadingState label="Loading the trend charts" />}>
                <TrendsSection trends={data.trends.data} names={names} />
              </Suspense>
            )
          }
        </QuerySection>
      )}

      {data.run.data && <VersionInfo result={data.run.data} />}
    </>
  );
}

function PageHeader({ organizationId, team, children }: { organizationId: string; team?: string; children?: React.ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Leadership Intelligence</h1>
        <p className="mt-1 text-sm text-ink-soft">
          Organization {organizationId}
          {team && <> &middot; team {team}</>}
        </p>
      </div>
      {children}
    </div>
  );
}
