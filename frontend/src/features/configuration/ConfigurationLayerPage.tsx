import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '@/app/providers/auth-context';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { FormError } from '@/components/ui/Form';
import { PageHeader } from '@/components/ui/PageHeader';
import { QuerySection } from '@/components/ui/QuerySection';
import { canChangeConfiguration } from '@/services/auth/principal';
import { useConfigLayers, useConfigVersions, useCopyConfigVersion, useCreateConfigDraft } from '@/services/queries/configuration';
import { formatDateTime } from '@/utils/presentation';
import { FixturesPanel } from './FixturesPanel';
import { layerName, STATUS_LABEL, statusTone, versionPath } from './labels';

/** The versions of one layer, newest first, with its fixtures. Copying an old version as a new draft is how a change is rolled back. */
export function ConfigurationLayerPage({ kind }: { kind: 'platform' | 'tenant' | 'organization' }) {
  const params = useParams();
  const organizationId = kind === 'organization' ? (params.organizationId ?? '') : '';
  const scope = kind === 'organization' ? 'ORGANIZATION' : kind === 'tenant' ? 'TENANT' : 'PLATFORM_DEFAULT';
  const navigate = useNavigate();
  const { principal } = useAuth();
  const canChange = canChangeConfiguration(principal);
  const layers = useConfigLayers();
  const versions = useConfigVersions(scope, organizationId);
  const create = useCreateConfigDraft();
  const copy = useCopyConfigVersion();
  const layer = (layers.data ?? []).find((l) => l.scope === scope && (scope !== 'ORGANIZATION' || l.scopeId === organizationId));
  const name = layerName(scope, organizationId || undefined);

  return (
    <div className="space-y-6">
      <PageHeader
        title={name}
        subtitle="Every version of this layer. A copy of an older version, made a new draft, is how to go back."
        actions={
          canChange ? (
            <Button
              variant="primary"
              disabled={create.isPending}
              onClick={() =>
                create.mutate(
                  { scope: scope === 'PLATFORM_DEFAULT' ? undefined : scope, organizationId: organizationId || undefined },
                  { onSuccess: (v) => navigate(versionPath(v.id)) },
                )
              }
            >
              {create.isPending ? 'Creating' : 'New draft'}
            </Button>
          ) : undefined
        }
      />
      <p className="text-sm">
        <Link className="underline" to="/configuration">
          All configuration layers
        </Link>
      </p>
      <FormError error={create.error ?? copy.error} />
      <QuerySection query={versions} label="the versions">
        {() => {
          const items = versions.data ?? [];
          return (
            <Card aria-label="Versions">
              <CardHeader title="Versions" />
              {items.length === 0 ? (
                <p className="px-5 py-4 text-sm text-ink-soft">This layer has no version yet.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-max text-sm">
                    <caption className="sr-only">Versions of {name}</caption>
                    <thead>
                      <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-faint">
                        <th scope="col" className="px-5 py-2 font-medium">Version</th>
                        <th scope="col" className="px-5 py-2 font-medium">Status</th>
                        <th scope="col" className="px-5 py-2 font-medium">Created</th>
                        <th scope="col" className="px-5 py-2 font-medium">Approved</th>
                        <th scope="col" className="px-5 py-2 font-medium">Calibration</th>
                        <th scope="col" className="px-5 py-2 font-medium">
                          <span className="sr-only">Actions</span>
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {items.map((v) => (
                        <tr key={v.id} className="border-b border-line/60">
                          <th scope="row" className="px-5 py-2 text-left font-medium">
                            <Link className="underline" to={versionPath(v.id)}>
                              Version {v.version}
                            </Link>
                          </th>
                          <td className="px-5 py-2">
                            <Badge tone={statusTone(v.status)}>{STATUS_LABEL[v.status ?? ''] ?? v.status}</Badge>
                          </td>
                          <td className="px-5 py-2 text-ink-soft">
                            {v.createdBy}, {formatDateTime(v.createdAt)}
                          </td>
                          <td className="px-5 py-2 text-ink-soft">{v.approvedBy ? `${v.approvedBy}, ${formatDateTime(v.approvedAt)}` : '—'}</td>
                          <td className="px-5 py-2">
                            <Badge tone={v.unvalidated ? 'medium' : 'ok'}>{v.unvalidated ? 'Unvalidated' : 'Stated'}</Badge>
                          </td>
                          <td className="px-5 py-2">
                            {canChange && (
                              <Button
                                variant="ghost"
                                aria-label={`Copy as draft, version ${v.version}`}
                                disabled={copy.isPending}
                                onClick={() => copy.mutate(v.id as string, { onSuccess: (d) => navigate(versionPath(d.id)) })}
                              >
                                Copy as draft
                              </Button>
                            )}
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
      <QuerySection query={layers} label="the layer">
        {() => <FixturesPanel layerId={layer?.id} canChange={canChange} />}
      </QuerySection>
    </div>
  );
}
