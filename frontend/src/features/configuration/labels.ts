import type { Tone } from '@/components/ui/Badge';
import type { ConfigLayer, ConfigVersion } from '@/types/api';

/** Words for the lifecycle (specv3.md Section 23.5); VALIDATING is shown as what it means in the product: waiting for another person. */
export const STATUS_LABEL: Record<string, string> = {
  DRAFT: 'Draft',
  VALIDATING: 'Awaiting approval',
  ACTIVE: 'Active',
  PAUSED: 'Paused',
  ARCHIVED: 'Archived',
};

export function statusTone(status: string | undefined): Tone {
  switch (status) {
    case 'ACTIVE':
      return 'ok';
    case 'VALIDATING':
      return 'accent';
    case 'DRAFT':
      return 'medium';
    case 'PAUSED':
      return 'low';
    default:
      return 'neutral';
  }
}

export const SCOPE_LABEL: Record<string, string> = {
  PLATFORM_DEFAULT: 'Platform default',
  TENANT: 'Tenant override',
  ORGANIZATION: 'Organization override',
};

export function layerName(scope: string | undefined, scopeId?: string | null): string {
  const base = SCOPE_LABEL[scope ?? ''] ?? 'Configuration';
  return scope === 'ORGANIZATION' && scopeId ? `${base} ${scopeId}` : base;
}

/** The address of the page of a layer's versions. */
export function layerPath(layer: Pick<ConfigLayer, 'scope' | 'scopeId'>): string {
  switch (layer.scope) {
    case 'PLATFORM_DEFAULT':
      return '/configuration/layers/platform';
    case 'ORGANIZATION':
      return `/configuration/layers/organization/${encodeURIComponent(layer.scopeId ?? '')}`;
    default:
      return '/configuration/layers/tenant';
  }
}

export const versionPath = (id: string | undefined) => `/configuration/versions/${id}`;

/** Whether a version is in a state the editor can change (only a draft is editable). */
export const editable = (version: Pick<ConfigVersion, 'status'>) => version.status === 'DRAFT';

/** The JSON of a stored document, laid out for reading; the text unchanged when it is not JSON. */
export function pretty(payload: string | undefined): string {
  if (!payload) return '';
  try {
    return JSON.stringify(JSON.parse(payload), null, 2);
  } catch {
    return payload;
  }
}
