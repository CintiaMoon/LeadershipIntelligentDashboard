import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/app/providers/auth-context';
import { Badge } from '@/components/ui/Badge';
import { Button, secondaryLinkClass } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { FormError } from '@/components/ui/Form';
import { PageHeader } from '@/components/ui/PageHeader';
import { QuerySection } from '@/components/ui/QuerySection';
import { EmptyState } from '@/components/ui/StateViews';
import { canChangeConfiguration } from '@/services/auth/principal';
import { useConfigLayers, useCreateConfigDraft } from '@/services/queries/configuration';
import type { ConfigLayer } from '@/types/api';
import { formatDateTime } from '@/utils/presentation';
import { layerName, layerPath, STATUS_LABEL, statusTone, versionPath } from './labels';

function LayerCard({ layer, canChange }: { layer: ConfigLayer; canChange: boolean }) {
  const navigate = useNavigate();
  const create = useCreateConfigDraft();
  const name = layerName(layer.scope, layer.scopeId);
  return (
    <Card aria-label={name} className="space-y-3 p-5">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h2 className="text-base font-semibold">{name}</h2>
        {layer.active ? <Badge tone={layer.active.unvalidated ? 'medium' : 'ok'}>{layer.active.unvalidated ? 'Unvalidated' : 'Calibration stated'}</Badge> : <Badge>No active version</Badge>}
      </div>
      <dl className="grid gap-2 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-ink-soft">Active version</dt>
          <dd>
            {layer.active ? (
              <Link className="underline" to={versionPath(layer.active.id)}>
                Version {layer.active.version}
              </Link>
            ) : (
              'None'
            )}
            {layer.active?.effectiveFrom && <span className="text-ink-soft"> since {formatDateTime(layer.active.effectiveFrom)}</span>}
          </dd>
        </div>
        <div>
          <dt className="text-ink-soft">Open draft</dt>
          <dd>
            {layer.open ? (
              <>
                <Link className="underline" to={versionPath(layer.open.id)}>
                  Version {layer.open.version}
                </Link>{' '}
                <Badge tone={statusTone(layer.open.status)}>{STATUS_LABEL[layer.open.status ?? ''] ?? layer.open.status}</Badge>
              </>
            ) : (
              'None'
            )}
          </dd>
        </div>
      </dl>
      <FormError error={create.error} />
      <div className="flex flex-wrap gap-2">
        <Link to={layerPath(layer)} className={secondaryLinkClass}>
          Versions and fixtures
        </Link>
        {canChange && !layer.open && (
          <Button
            disabled={create.isPending}
            onClick={() =>
              create.mutate(
                { scope: layer.scope === 'PLATFORM_DEFAULT' ? undefined : layer.scope, organizationId: layer.scope === 'ORGANIZATION' ? layer.scopeId : undefined },
                { onSuccess: (v) => navigate(versionPath(v.id)) },
              )
            }
          >
            {create.isPending ? 'Creating' : `New draft of ${name}`}
          </Button>
        )}
      </div>
    </Card>
  );
}

/** The configuration layers the signed-in person manages (decision D52): the platform default, or the tenant and its organizations. */
export function ConfigurationPage() {
  const layers = useConfigLayers();
  const { principal } = useAuth();
  const canChange = canChangeConfiguration(principal);
  return (
    <div className="space-y-6">
      <PageHeader
        title="Configuration"
        subtitle="The rules the scoring follows. A change is a new version: drafted, checked against fixtures, approved by a second person, then active. Active versions never change."
      />
      <QuerySection query={layers} label="the configuration layers">
        {() => {
          const items = layers.data ?? [];
          if (items.length === 0) return <EmptyState title="No configuration layers" description="There is nothing to manage here yet." />;
          return (
            <div className="grid gap-4 lg:grid-cols-2">
              {items.map((l) => (
                <LayerCard key={`${l.scope}/${l.scopeId ?? ''}`} layer={l} canChange={canChange} />
              ))}
            </div>
          );
        }}
      </QuerySection>
    </div>
  );
}
